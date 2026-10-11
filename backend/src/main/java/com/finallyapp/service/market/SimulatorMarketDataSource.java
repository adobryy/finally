package com.finallyapp.service.market;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.random.RandomGenerator;

import org.springframework.scheduling.annotation.Scheduled;

import com.finallyapp.repository.PriceCache;

/** Simulates correlated prices with geometric Brownian motion and occasional jumps. */
public class SimulatorMarketDataSource implements MarketDataSource {

	static final double TIME_SCALE = 100;
	static final double DT = 0.5 / (252 * 6.5 * 3600) * TIME_SCALE;
	static final double JUMP_PROBABILITY = 0.001;
	static final double JUMP_MIN = 0.02;
	static final double JUMP_MAX = 0.05;

	private final PriceCache priceCache;
	private final RandomGenerator random;
	private final Map<String, State> states = new ConcurrentHashMap<>();

	private record State(TickerParams params, double price) {
	}

	public SimulatorMarketDataSource(PriceCache priceCache, RandomGenerator random) {
		this.priceCache = priceCache;
		this.random = random;
	}

	@Override
	public synchronized void track(String ticker) {
		if (states.containsKey(ticker)) {
			return;
		}
		TickerParams params = TickerParams.KNOWN.getOrDefault(ticker, TickerParams.DEFAULT);
		double seed = params.seedPrice() > 0 ? params.seedPrice() : random.nextDouble(50, 300);
		states.put(ticker, new State(params, seed));
		priceCache.update(ticker, seed);
	}

	@Override
	public void untrack(String ticker) {
		states.remove(ticker);
	}

	/** Advance every tracked ticker by one 500ms tick. */
	@Scheduled(fixedRate = 500)
	public synchronized void tick() {
		double market = random.nextGaussian();
		states.replaceAll((ticker, state) -> {
			double price = advance(state, market);
			priceCache.update(ticker, price);
			return new State(state.params(), price);
		});
	}

	private double advance(State state, double market) {
		TickerParams p = state.params();
		double z = correlatedShock(p.beta(), market, random.nextGaussian());
		double price = gbmStep(state.price(), p.mu(), p.sigma(), DT, z);
		return random.nextDouble() < JUMP_PROBABILITY ? price * jumpFactor(random) : price;
	}

	/** One-factor model: mixes the market shock with an idiosyncratic shock. */
	static double correlatedShock(double beta, double market, double idiosyncratic) {
		return beta * market + Math.sqrt(1 - beta * beta) * idiosyncratic;
	}

	/** Exact GBM step: S' = S * exp((mu - sigma^2/2) dt + sigma sqrt(dt) z). */
	static double gbmStep(double price, double mu, double sigma, double dt, double z) {
		return price * Math.exp((mu - sigma * sigma / 2) * dt + sigma * Math.sqrt(dt) * z);
	}

	/** Multiplier for a sudden 2-5% move, up or down. */
	static double jumpFactor(RandomGenerator random) {
		double size = random.nextDouble(JUMP_MIN, JUMP_MAX);
		return random.nextBoolean() ? 1 + size : 1 - size;
	}
}
