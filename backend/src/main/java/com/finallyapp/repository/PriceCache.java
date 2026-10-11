package com.finallyapp.repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.finallyapp.model.PriceQuote;
import com.finallyapp.model.Prices;

/** In-memory latest quote per ticker, written by the active market data source. */
@Component
public class PriceCache {

	private final Map<String, PriceQuote> quotes = new ConcurrentHashMap<>();

	/** Store a new price; the open price is the first price seen for the ticker. */
	public PriceQuote update(String ticker, double price) {
		return quotes.compute(ticker, (t, old) -> {
			double rounded = Prices.round2(price);
			double open = old == null ? rounded : old.openPrice();
			return next(t, rounded, open, old);
		});
	}

	/** Store a new price with an explicit open price (e.g. the previous close). */
	public PriceQuote update(String ticker, double price, double openPrice) {
		return quotes.compute(ticker,
				(t, old) -> next(t, Prices.round2(price), Prices.round2(openPrice), old));
	}

	public PriceQuote get(String ticker) {
		return quotes.get(ticker);
	}

	public List<PriceQuote> all() {
		return List.copyOf(quotes.values());
	}

	public void remove(String ticker) {
		quotes.remove(ticker);
	}

	private static PriceQuote next(String ticker, double price, double open, PriceQuote old) {
		double previous = old == null ? price : old.price();
		return new PriceQuote(ticker, price, previous, open, Instant.now());
	}
}
