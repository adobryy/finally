package com.finallyapp.repository.client;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.finallyapp.model.LlmResponse;
import com.finallyapp.model.LlmTrade;
import com.finallyapp.model.LlmWatchlistChange;
import com.finallyapp.model.Side;
import com.finallyapp.model.WatchlistAction;

import reactor.core.publisher.Mono;

/** Deterministic responses to the last user message, for tests and development without an API key. */
public class MockLlmClient implements LlmClient {

	static final String DEFAULT_MESSAGE = "Mock: I am FinAlly, your AI trading assistant.";

	private static final Pattern BUY = pattern("^buy\\s+(\\d+(\\.\\d+)?)\\s+([A-Z.]+)$");
	private static final Pattern SELL = pattern("^sell\\s+(\\d+(\\.\\d+)?)\\s+([A-Z.]+)$");
	private static final Pattern WATCH = pattern("^watch\\s+([A-Z.]+)$");
	private static final Pattern UNWATCH = pattern("^unwatch\\s+([A-Z.]+)$");

	@Override
	public Mono<LlmResponse> complete(List<LlmMessage> messages) {
		return Mono.just(respond(messages.getLast().content().trim()));
	}

	static LlmResponse respond(String message) {
		Matcher m;
		if ((m = BUY.matcher(message)).matches()) {
			return trade(Side.BUY, "buying", m);
		}
		if ((m = SELL.matcher(message)).matches()) {
			return trade(Side.SELL, "selling", m);
		}
		if ((m = WATCH.matcher(message)).matches()) {
			return watch(WatchlistAction.ADD, "watching", m.group(1));
		}
		if ((m = UNWATCH.matcher(message)).matches()) {
			return watch(WatchlistAction.REMOVE, "unwatching", m.group(1));
		}
		return new LlmResponse(DEFAULT_MESSAGE, List.of(), List.of());
	}

	private static LlmResponse trade(Side side, String verb, Matcher m) {
		String ticker = m.group(3).toUpperCase();
		return new LlmResponse("Mock: " + verb + " " + m.group(1) + " " + ticker,
				List.of(new LlmTrade(ticker, side, Double.parseDouble(m.group(1)))), List.of());
	}

	private static LlmResponse watch(WatchlistAction action, String verb, String rawTicker) {
		String ticker = rawTicker.toUpperCase();
		return new LlmResponse("Mock: " + verb + " " + ticker,
				List.of(), List.of(new LlmWatchlistChange(ticker, action)));
	}

	private static Pattern pattern(String regex) {
		return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
	}
}
