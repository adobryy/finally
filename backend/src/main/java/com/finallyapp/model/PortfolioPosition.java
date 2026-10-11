package com.finallyapp.model;

import static com.finallyapp.model.Prices.round2;

/** A holding valued at the latest price; price-derived fields are null until the first quote. */
public record PortfolioPosition(String ticker, double quantity, double avgCost, Double currentPrice,
		Double marketValue, Double unrealizedPnl, Double pnlPercent) {

	public static PortfolioPosition of(Position position, PriceQuote quote) {
		double avg = round2(position.avgCost());
		if (quote == null) {
			return new PortfolioPosition(position.ticker(), position.quantity(), avg, null, null, null, null);
		}
		double price = quote.price();
		double qty = position.quantity();
		return new PortfolioPosition(position.ticker(), qty, avg, price,
				round2(qty * price),
				round2((price - position.avgCost()) * qty),
				round2((price - position.avgCost()) / position.avgCost() * 100));
	}

	public boolean priced() {
		return currentPrice != null;
	}
}
