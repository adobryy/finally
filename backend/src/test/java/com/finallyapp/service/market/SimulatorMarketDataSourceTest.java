package com.finallyapp.service.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.finallyapp.repository.PriceCache;

class SimulatorMarketDataSourceTest {

	private final PriceCache cache = new PriceCache();
	private final SimulatorMarketDataSource simulator = new SimulatorMarketDataSource(cache, new Random(42));

	@Test
	void trackSeedsKnownTickerPriceImmediately() {
		simulator.track("AAPL");

		assertThat(cache.get("AAPL").price()).isEqualTo(190.0);
	}

	@Test
	void unknownTickerSeedsWithinRange() {
		simulator.track("PYPL");

		assertThat(cache.get("PYPL").price()).isBetween(50.0, 300.0);
	}

	@Test
	void trackIsIdempotent() {
		simulator.track("AAPL");
		simulator.tick();
		double afterTick = cache.get("AAPL").price();

		simulator.track("AAPL");

		assertThat(cache.get("AAPL").price()).isEqualTo(afterTick);
	}

	@Test
	void tickMovesPrices() {
		simulator.track("AAPL");
		for (int i = 0; i < 10; i++) {
			simulator.tick();
		}

		assertThat(cache.get("AAPL").price()).isNotEqualTo(190.0);
		assertThat(cache.get("AAPL").openPrice()).isEqualTo(190.0);
	}

	@Test
	void untrackStopsUpdates() {
		simulator.track("AAPL");
		simulator.untrack("AAPL");
		cache.remove("AAPL");

		simulator.tick();

		assertThat(cache.get("AAPL")).isNull();
	}

	@Test
	void pricesStayPositive() {
		TickerParams.KNOWN.keySet().forEach(simulator::track);
		for (int i = 0; i < 20_000; i++) {
			simulator.tick();
		}

		assertThat(cache.all()).allSatisfy(q -> assertThat(q.price()).isPositive());
	}

	@Test
	void sameSeedGivesSamePrices() {
		PriceCache otherCache = new PriceCache();
		SimulatorMarketDataSource other = new SimulatorMarketDataSource(otherCache, new Random(42));
		simulator.track("AAPL");
		other.track("AAPL");
		for (int i = 0; i < 100; i++) {
			simulator.tick();
			other.tick();
		}

		assertThat(cache.get("AAPL").price()).isEqualTo(otherCache.get("AAPL").price());
	}

	@Test
	void gbmStepMatchesFormula() {
		double dt = SimulatorMarketDataSource.DT;
		double expected = 100 * Math.exp((0.1 - 0.2 * 0.2 / 2) * dt + 0.2 * Math.sqrt(dt) * 1.5);

		assertThat(SimulatorMarketDataSource.gbmStep(100, 0.1, 0.2, dt, 1.5)).isEqualTo(expected);
		assertThat(SimulatorMarketDataSource.gbmStep(100, 0.1, 0.2, dt, 1.5)).isGreaterThan(100);
		assertThat(SimulatorMarketDataSource.gbmStep(100, 0.1, 0.2, dt, -1.5)).isLessThan(100);
	}

	@Test
	void tickSizeIsVisibleAtCentPrecision() {
		double move = 190 * 0.25 * Math.sqrt(SimulatorMarketDataSource.DT);

		assertThat(move).isBetween(0.10, 0.20);
	}

	@Test
	void correlatedShockMixesFactors() {
		assertThat(SimulatorMarketDataSource.correlatedShock(1, 0.7, -2)).isEqualTo(0.7);
		assertThat(SimulatorMarketDataSource.correlatedShock(0, 0.7, -2)).isEqualTo(-2);
		assertThat(SimulatorMarketDataSource.correlatedShock(0.6, 1, 1)).isCloseTo(1.4, within(1e-12));
	}

	/** Jumps are uncorrelated and dwarf a tick's diffusion, so returns above 1% are excluded. */
	@Test
	void sameSectorTickersAreCorrelated() {
		simulator.track("AAPL");
		simulator.track("MSFT");
		List<Double> aapl = new ArrayList<>();
		List<Double> msft = new ArrayList<>();
		double prevA = cache.get("AAPL").price();
		double prevM = cache.get("MSFT").price();
		for (int i = 0; i < 5_000; i++) {
			simulator.tick();
			double a = cache.get("AAPL").price();
			double m = cache.get("MSFT").price();
			double ra = Math.log(a / prevA);
			double rm = Math.log(m / prevM);
			if (Math.abs(ra) < 0.01 && Math.abs(rm) < 0.01) {
				aapl.add(ra);
				msft.add(rm);
			}
			prevA = a;
			prevM = m;
		}

		assertThat(correlation(aapl, msft)).isCloseTo(0.64, within(0.05));
	}

	@Test
	void jumpFactorIsBetweenTwoAndFivePercent() {
		Random random = new Random(7);
		boolean up = false;
		boolean down = false;
		for (int i = 0; i < 10_000; i++) {
			double factor = SimulatorMarketDataSource.jumpFactor(random);
			assertThat(Math.abs(factor - 1)).isBetween(0.02, 0.05);
			up |= factor > 1;
			down |= factor < 1;
		}

		assertThat(up).isTrue();
		assertThat(down).isTrue();
	}

	private static double correlation(List<Double> x, List<Double> y) {
		double mx = x.stream().mapToDouble(d -> d).average().orElseThrow();
		double my = y.stream().mapToDouble(d -> d).average().orElseThrow();
		double sxy = 0;
		double sxx = 0;
		double syy = 0;
		for (int i = 0; i < x.size(); i++) {
			double dx = x.get(i) - mx;
			double dy = y.get(i) - my;
			sxy += dx * dy;
			sxx += dx * dx;
			syy += dy * dy;
		}
		return sxy / Math.sqrt(sxx * syy);
	}
}
