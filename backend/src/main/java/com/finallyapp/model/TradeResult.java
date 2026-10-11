package com.finallyapp.model;

/** An executed market order and the cash balance after it. */
public record TradeResult(String ticker, Side side, double quantity, double price, double cashBalance) {

	public TradeResult {
		cashBalance = Prices.round2(cashBalance);
	}
}
