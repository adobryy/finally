package com.finallyapp.model;

import static com.finallyapp.model.Prices.round2;

import java.util.List;

/** Cash plus valued positions; totals include only priced positions. */
public record Portfolio(double cashBalance, double totalValue, double unrealizedPnl,
		List<PortfolioPosition> positions) {

	public static Portfolio of(double cash, List<PortfolioPosition> positions) {
		List<PortfolioPosition> priced = positions.stream().filter(PortfolioPosition::priced).toList();
		double marketValue = priced.stream().mapToDouble(PortfolioPosition::marketValue).sum();
		double pnl = priced.stream().mapToDouble(PortfolioPosition::unrealizedPnl).sum();
		return new Portfolio(round2(cash), round2(cash + marketValue), round2(pnl), positions);
	}
}
