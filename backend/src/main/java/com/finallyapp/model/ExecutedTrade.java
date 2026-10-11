package com.finallyapp.model;

/** A trade the assistant executed, with its fill price. */
public record ExecutedTrade(String ticker, Side side, double quantity, double price) {

	public static ExecutedTrade of(TradeResult result) {
		return new ExecutedTrade(result.ticker(), result.side(), result.quantity(), result.price());
	}
}
