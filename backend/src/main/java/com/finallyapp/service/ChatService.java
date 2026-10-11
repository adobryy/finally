package com.finallyapp.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.finallyapp.model.ChatActions;
import com.finallyapp.model.ChatMessage;
import com.finallyapp.model.ChatReply;
import com.finallyapp.model.DefaultUser;
import com.finallyapp.model.ExecutedTrade;
import com.finallyapp.model.LlmResponse;
import com.finallyapp.model.LlmTrade;
import com.finallyapp.model.LlmWatchlistChange;
import com.finallyapp.model.WatchlistAction;
import com.finallyapp.repository.ChatMessageRepository;
import com.finallyapp.repository.client.LlmClient;
import com.finallyapp.repository.client.LlmUnavailableException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Chat flow: context, LLM call, watchlist changes, then trades, then persistence. */
@Service
public class ChatService {

	static final int PROMPT_HISTORY = 20;
	static final int HISTORY_LIMIT = 50;
	static final String FAILURE_MESSAGE = "Sorry, I could not reach the AI assistant just now. Please try again.";

	private static final Logger log = LoggerFactory.getLogger(ChatService.class);

	private final LlmClient llm;
	private final ChatPromptBuilder prompts;
	private final ChatMessageRepository messages;
	private final PortfolioService portfolioService;
	private final WatchlistService watchlistService;

	public ChatService(LlmClient llm, ChatPromptBuilder prompts, ChatMessageRepository messages,
			PortfolioService portfolioService, WatchlistService watchlistService) {
		this.llm = llm;
		this.prompts = prompts;
		this.messages = messages;
		this.portfolioService = portfolioService;
		this.watchlistService = watchlistService;
	}

	/** One outcome of an action: the applied value, or the error shown to the user. */
	private record Outcome<T>(T value, String error) {

		static <T> Mono<Outcome<T>> of(Mono<T> action) {
			return action.map(v -> new Outcome<T>(v, null))
					.onErrorResume(DomainException.class, e -> Mono.just(new Outcome<>(null, e.getMessage())));
		}
	}

	/** Answer a user message, executing the actions the assistant decides on. */
	public Mono<ChatReply> handle(String userMessage) {
		Instant receivedAt = Instant.now();
		return Mono.zip(portfolioService.portfolio(), watchlistService.list(), blocking(this::promptHistory))
				.map(ctx -> prompts.build(ctx.getT1(), ctx.getT2(), ctx.getT3(), userMessage))
				.flatMap(prompt -> llm.complete(prompt)
						.flatMap(this::execute)
						.onErrorResume(LlmUnavailableException.class, this::failure))
				.flatMap(reply -> blocking(() -> save(userMessage, receivedAt, reply)));
	}

	/** The latest 50 messages, oldest first. */
	public Mono<List<ChatMessage>> history() {
		return blocking(() -> messages.findRecent(DefaultUser.ID, HISTORY_LIMIT));
	}

	private Mono<ChatReply> execute(LlmResponse response) {
		Mono<List<Outcome<LlmWatchlistChange>>> changes = Flux.fromIterable(response.watchlistChanges())
				.concatMap(change -> Outcome.of(applyChange(change)))
				.collectList();
		Mono<List<Outcome<ExecutedTrade>>> trades = Flux.fromIterable(response.trades())
				.concatMap(trade -> Outcome.of(applyTrade(trade)))
				.collectList();
		return changes.zipWhen(c -> trades)
				.map(t -> new ChatReply(response.message(), actions(t.getT1(), t.getT2())));
	}

	private Mono<LlmWatchlistChange> applyChange(LlmWatchlistChange change) {
		if (change.action() == WatchlistAction.ADD) {
			return watchlistService.add(change.ticker())
					.map(item -> new LlmWatchlistChange(item.ticker(), WatchlistAction.ADD));
		}
		return watchlistService.remove(change.ticker())
				.then(Mono.fromCallable(() -> new LlmWatchlistChange(Tickers.normalize(change.ticker()), WatchlistAction.REMOVE)));
	}

	private Mono<ExecutedTrade> applyTrade(LlmTrade trade) {
		return portfolioService.trade(trade.ticker(), trade.side(), trade.quantity()).map(ExecutedTrade::of);
	}

	private static ChatActions actions(List<Outcome<LlmWatchlistChange>> changes, List<Outcome<ExecutedTrade>> trades) {
		List<String> errors = Stream.concat(changes.stream(), trades.stream())
				.map(Outcome::error)
				.filter(Objects::nonNull)
				.toList();
		return new ChatActions(
				trades.stream().map(Outcome::value).filter(Objects::nonNull).toList(),
				changes.stream().map(Outcome::value).filter(Objects::nonNull).toList(),
				errors);
	}

	private Mono<ChatReply> failure(LlmUnavailableException e) {
		log.warn("LLM call failed", e);
		return Mono.just(new ChatReply(FAILURE_MESSAGE, ChatActions.error(e.getMessage())));
	}

	private List<ChatMessage> promptHistory() {
		return messages.findRecent(DefaultUser.ID, PROMPT_HISTORY);
	}

	private ChatReply save(String userMessage, Instant receivedAt, ChatReply reply) {
		messages.insert(DefaultUser.ID, new ChatMessage(ChatMessage.USER, userMessage, null, receivedAt));
		messages.insert(DefaultUser.ID, new ChatMessage(ChatMessage.ASSISTANT, reply.message(), reply.actions(), Instant.now()));
		return reply;
	}

	private static <T> Mono<T> blocking(Callable<T> call) {
		return Mono.fromCallable(call).subscribeOn(Schedulers.boundedElastic());
	}
}
