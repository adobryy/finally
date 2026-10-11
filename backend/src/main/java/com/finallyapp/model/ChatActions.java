package com.finallyapp.model;

import java.util.List;

/** What an assistant reply actually did: executed trades, applied watchlist changes and errors. */
public record ChatActions(List<ExecutedTrade> trades, List<LlmWatchlistChange> watchlistChanges, List<String> errors) {

	public static ChatActions error(String error) {
		return new ChatActions(List.of(), List.of(), List.of(error));
	}
}
