package com.finallyapp.model;

/** A watchlist change the assistant wants to make. */
public record LlmWatchlistChange(String ticker, WatchlistAction action) {
}
