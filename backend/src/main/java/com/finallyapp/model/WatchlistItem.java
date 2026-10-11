package com.finallyapp.model;

/** A watched ticker with its latest price; price fields are null until the first quote. */
public record WatchlistItem(String ticker, Double price, Double changePercent, Direction direction) {

	public static WatchlistItem of(String ticker, PriceQuote quote) {
		return quote == null
				? new WatchlistItem(ticker, null, null, null)
				: new WatchlistItem(ticker, quote.price(), quote.changePercent(), quote.direction());
	}
}
