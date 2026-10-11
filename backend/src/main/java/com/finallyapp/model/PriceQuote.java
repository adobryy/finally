package com.finallyapp.model;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Latest price of a ticker, with the previous price and the open price for daily change. */
public record PriceQuote(String ticker, double price, double previousPrice,
		double openPrice, Instant timestamp) {

	@JsonProperty
	public Direction direction() {
		return price > previousPrice ? Direction.UP
				: price < previousPrice ? Direction.DOWN
				: Direction.FLAT;
	}

	/** Change versus the open price, in percent, rounded to 2 decimals. */
	@JsonProperty
	public double changePercent() {
		return Prices.round2((price - openPrice) / openPrice * 100);
	}
}
