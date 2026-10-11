package com.finallyapp.service.market;

/** A producer of live prices that writes into the shared {@code PriceCache}. */
public interface MarketDataSource {

	/** Start producing prices for the given ticker (idempotent). The simulator puts a seed price in PriceCache immediately. */
	void track(String ticker);

	/** Stop producing prices for a ticker. */
	void untrack(String ticker);
}
