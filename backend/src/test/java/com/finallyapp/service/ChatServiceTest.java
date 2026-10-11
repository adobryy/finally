package com.finallyapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.finallyapp.model.ChatActions;
import com.finallyapp.model.ChatMessage;
import com.finallyapp.model.ChatReply;
import com.finallyapp.model.ExecutedTrade;
import com.finallyapp.model.LlmResponse;
import com.finallyapp.model.LlmTrade;
import com.finallyapp.model.LlmWatchlistChange;
import com.finallyapp.model.Side;
import com.finallyapp.model.WatchlistAction;
import com.finallyapp.repository.ChatMessageRepository;
import com.finallyapp.repository.PositionRepository;
import com.finallyapp.repository.PriceCache;
import com.finallyapp.repository.SnapshotRepository;
import com.finallyapp.repository.TestDatabase;
import com.finallyapp.repository.TradeRepository;
import com.finallyapp.repository.UserProfileRepository;
import com.finallyapp.repository.WatchlistRepository;
import com.finallyapp.repository.client.LlmClient;
import com.finallyapp.repository.client.LlmMessage;
import com.finallyapp.repository.client.LlmUnavailableException;
import com.finallyapp.service.market.SimulatorMarketDataSource;

import reactor.core.publisher.Mono;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class ChatServiceTest {

	@TempDir
	Path tempDir;

	private final JsonMapper mapper = JsonMapper.builder()
			.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
			.build();
	private final PriceCache cache = new PriceCache();
	private final List<List<LlmMessage>> prompts = new ArrayList<>();
	private Mono<LlmResponse> nextResponse;
	private TestDatabase db;
	private ChatService service;

	@BeforeEach
	void setUp() {
		db = TestDatabase.create(tempDir);
		db.insertProfile(10000);
		WatchlistRepository watchlist = new WatchlistRepository(db.jdbc());
		PositionRepository positions = new PositionRepository(db.jdbc());
		SimulatorMarketDataSource simulator = new SimulatorMarketDataSource(cache, new Random(1));
		MarketService market = new MarketService(simulator, cache, watchlist, positions);
		PortfolioService portfolio = new PortfolioService(new UserProfileRepository(db.jdbc()), positions,
				new TradeRepository(db.jdbc()), new SnapshotRepository(db.jdbc()), cache, market, db.transactions());
		WatchlistService watchlistService = new WatchlistService(watchlist, market, cache);
		LlmClient llm = messages -> {
			prompts.add(messages);
			return nextResponse;
		};
		service = new ChatService(llm, new ChatPromptBuilder(mapper), new ChatMessageRepository(db.jdbc(), mapper),
				portfolio, watchlistService);
		watchlistService.add("AAPL").block();
	}

	@AfterEach
	void tearDown() {
		db.close();
	}

	@Test
	void executesTradeAndPersistsBothMessages() {
		respond("Bought 5 AAPL.", List.of(new LlmTrade("aapl", Side.BUY, 5)), List.of());

		ChatReply reply = service.handle("buy 5 AAPL").block();

		assertThat(reply).isEqualTo(new ChatReply("Bought 5 AAPL.",
				new ChatActions(List.of(new ExecutedTrade("AAPL", Side.BUY, 5, 190)), List.of(), List.of())));
		assertThat(service.history().block()).extracting(ChatMessage::role, ChatMessage::content, ChatMessage::actions)
				.containsExactly(
						tuple("user", "buy 5 AAPL", null),
						tuple("assistant", "Bought 5 AAPL.", reply.actions()));
	}

	@Test
	void failedTradeIsReportedInErrorsAndOthersStillRun() {
		respond("Trying.", List.of(new LlmTrade("AAPL", Side.SELL, 100), new LlmTrade("AAPL", Side.BUY, 1)), List.of());

		ChatReply reply = service.handle("do it").block();

		assertThat(reply.actions().trades()).extracting(ExecutedTrade::ticker).containsExactly("AAPL");
		assertThat(reply.actions().errors()).containsExactly("Insufficient shares to sell 100 AAPL: own 0");
	}

	@Test
	void watchlistChangesRunBeforeTradesSoNewTickerCanBeBought() {
		respond("Watching and buying PYPL.", List.of(new LlmTrade("PYPL", Side.BUY, 2)),
				List.of(new LlmWatchlistChange("pypl", WatchlistAction.ADD)));

		ChatReply reply = service.handle("add PYPL and buy 2").block();

		assertThat(reply.actions().watchlistChanges()).containsExactly(new LlmWatchlistChange("PYPL", WatchlistAction.ADD));
		assertThat(reply.actions().trades()).extracting(ExecutedTrade::ticker).containsExactly("PYPL");
		assertThat(reply.actions().errors()).isEmpty();
	}

	@Test
	void watchlistFailuresAreCollected() {
		respond("ok", List.of(), List.of(
				new LlmWatchlistChange("AAPL", WatchlistAction.ADD),
				new LlmWatchlistChange("ZZZZ", WatchlistAction.REMOVE),
				new LlmWatchlistChange("BAD1", WatchlistAction.ADD),
				new LlmWatchlistChange("aapl", WatchlistAction.REMOVE)));

		ChatReply reply = service.handle("shuffle").block();

		assertThat(reply.actions().watchlistChanges()).containsExactly(new LlmWatchlistChange("AAPL", WatchlistAction.REMOVE));
		assertThat(reply.actions().errors()).containsExactly(
				"AAPL is already on the watchlist",
				"ZZZZ is not on the watchlist",
				"Invalid ticker 'BAD1': expected 1-10 letters or dots");
	}

	@Test
	void llmFailureGivesGracefulPersistedReply() {
		nextResponse = Mono.error(new LlmUnavailableException("LLM request failed: timeout", null));

		ChatReply reply = service.handle("hello").block();

		assertThat(reply).isEqualTo(new ChatReply(ChatService.FAILURE_MESSAGE,
				ChatActions.error("LLM request failed: timeout")));
		assertThat(service.history().block()).hasSize(2);
	}

	@Test
	void promptHasSystemPromptContextHistoryAndNewMessage() {
		respond("Bought.", List.of(new LlmTrade("AAPL", Side.BUY, 1)), List.of());
		service.handle("buy 1 AAPL").block();
		respond("Fine.", List.of(), List.of());

		service.handle("how am I doing?").block();

		List<LlmMessage> prompt = prompts.getLast();
		assertThat(prompt).extracting(LlmMessage::role).containsExactly("system", "system", "user", "assistant", "user");
		assertThat(prompt.get(0).content()).startsWith("You are FinAlly, an AI trading assistant");
		assertThat(prompt.get(1).content()).startsWith("Portfolio context: {")
				.contains("\"cash_balance\":9810.0").contains("\"ticker\":\"AAPL\"").contains("\"watchlist\":[");
		assertThat(prompt.get(3).content()).startsWith("Bought.\nActions: {\"trades\":[{\"ticker\":\"AAPL\"");
		assertThat(prompt.get(4)).isEqualTo(LlmMessage.user("how am I doing?"));
	}

	private void respond(String message, List<LlmTrade> trades, List<LlmWatchlistChange> changes) {
		nextResponse = Mono.just(new LlmResponse(message, trades, changes));
	}
}
