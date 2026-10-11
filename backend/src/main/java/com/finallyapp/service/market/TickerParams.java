package com.finallyapp.service.market;

import java.util.Map;

/**
 * Simulation parameters for one ticker.
 *
 * @param seedPrice starting price
 * @param mu annual drift
 * @param sigma annual volatility
 * @param beta loading on the shared market factor, in [0, 1]
 */
public record TickerParams(double seedPrice, double mu, double sigma, double beta) {

	static final TickerParams DEFAULT = new TickerParams(0, 0.05, 0.30, 0.5);

	static final Map<String, TickerParams> KNOWN = Map.of(
			"AAPL", new TickerParams(190, 0.08, 0.25, 0.8),
			"GOOGL", new TickerParams(175, 0.08, 0.28, 0.8),
			"MSFT", new TickerParams(420, 0.08, 0.25, 0.8),
			"AMZN", new TickerParams(185, 0.09, 0.30, 0.8),
			"TSLA", new TickerParams(250, 0.10, 0.55, 0.7),
			"NVDA", new TickerParams(120, 0.12, 0.50, 0.85),
			"META", new TickerParams(500, 0.09, 0.35, 0.8),
			"JPM", new TickerParams(200, 0.06, 0.20, 0.4),
			"V", new TickerParams(280, 0.06, 0.18, 0.4),
			"NFLX", new TickerParams(650, 0.08, 0.40, 0.6));
}
