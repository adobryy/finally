package com.finallyapp.repository.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.finallyapp.model.LlmResponse;
import com.finallyapp.model.LlmTrade;
import com.finallyapp.model.LlmWatchlistChange;
import com.finallyapp.model.Side;
import com.finallyapp.model.WatchlistAction;

class MockLlmClientTest {

	@Test
	void buyAndSellPatterns() {
		assertThat(MockLlmClient.respond("buy 5 AAPL")).isEqualTo(new LlmResponse("Mock: buying 5 AAPL",
				List.of(new LlmTrade("AAPL", Side.BUY, 5)), List.of()));
		assertThat(MockLlmClient.respond("SELL 2.5 brk.b").trades())
				.containsExactly(new LlmTrade("BRK.B", Side.SELL, 2.5));
	}

	@Test
	void watchAndUnwatchAreDistinct() {
		assertThat(MockLlmClient.respond("watch PYPL").watchlistChanges())
				.containsExactly(new LlmWatchlistChange("PYPL", WatchlistAction.ADD));
		assertThat(MockLlmClient.respond("unwatch pypl").watchlistChanges())
				.containsExactly(new LlmWatchlistChange("PYPL", WatchlistAction.REMOVE));
	}

	@Test
	void anythingElseGetsFixedMessage() {
		for (String message : List.of("hello", "buy AAPL", "please buy 5 AAPL", "buy 5 AAPL now", "watch")) {
			LlmResponse response = MockLlmClient.respond(message);
			assertThat(response.message()).isEqualTo(MockLlmClient.DEFAULT_MESSAGE);
			assertThat(response.trades()).isEmpty();
			assertThat(response.watchlistChanges()).isEmpty();
		}
	}

	@Test
	void completeAnswersLastMessageTrimmed() {
		LlmResponse response = new MockLlmClient().complete(List.of(
				LlmMessage.system("prompt"), LlmMessage.user("hello"), LlmMessage.user("  buy 1 V  "))).block();

		assertThat(response.trades()).containsExactly(new LlmTrade("V", Side.BUY, 1));
	}
}
