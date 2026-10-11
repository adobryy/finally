package com.finallyapp.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Random;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.finallyapp.model.Direction;
import com.finallyapp.model.WatchlistItem;
import com.finallyapp.repository.PositionRepository;
import com.finallyapp.repository.PriceCache;
import com.finallyapp.repository.TestDatabase;
import com.finallyapp.repository.WatchlistRepository;
import com.finallyapp.service.market.SimulatorMarketDataSource;

import reactor.test.StepVerifier;

class WatchlistServiceTest {

	@TempDir
	Path tempDir;

	private final PriceCache cache = new PriceCache();
	private TestDatabase db;
	private SimulatorMarketDataSource simulator;
	private MarketService marketService;
	private WatchlistService service;

	@BeforeEach
	void setUp() {
		db = TestDatabase.create(tempDir);
		WatchlistRepository watchlist = new WatchlistRepository(db.jdbc());
		simulator = new SimulatorMarketDataSource(cache, new Random(1));
		marketService = new MarketService(simulator, cache, watchlist, new PositionRepository(db.jdbc()));
		service = new WatchlistService(watchlist, marketService, cache);
	}

	@AfterEach
	void tearDown() {
		db.close();
	}

	@Test
	void addNormalizesTracksAndReturnsPrice() {
		WatchlistItem item = service.add(" aapl ").block();

		assertThat(item).isEqualTo(new WatchlistItem("AAPL", 190.0, 0.0, Direction.FLAT));
		assertThat(cache.get("AAPL")).isNotNull();
	}

	@Test
	void listReturnsTickersWithPricesOrNulls() {
		service.add("AAPL").block();
		db.jdbc().sql("INSERT INTO watchlist (id, ticker, added_at) VALUES ('x', 'MSFT', '9999')").update();

		assertThat(service.list().block()).containsExactly(
				new WatchlistItem("AAPL", 190.0, 0.0, Direction.FLAT),
				new WatchlistItem("MSFT", null, null, null));
	}

	@Test
	void duplicateAddIsRejected() {
		service.add("AAPL").block();

		StepVerifier.create(service.add("aapl"))
				.expectError(TickerAlreadyWatchedException.class)
				.verify();
	}

	@Test
	void invalidTickerIsRejected() {
		StepVerifier.create(service.add("BRK-B")).expectError(InvalidTickerException.class).verify();
		StepVerifier.create(service.add("TOOLONGTICKER")).expectError(InvalidTickerException.class).verify();
		StepVerifier.create(service.remove("1")).expectError(InvalidTickerException.class).verify();
	}

	@Test
	void removeUntracksUnheldTicker() {
		service.add("AAPL").block();

		service.remove("aapl").block();

		assertThat(service.list().block()).isEmpty();
		assertThat(cache.get("AAPL")).isNull();
		simulator.tick();
		assertThat(cache.get("AAPL")).isNull();
	}

	@Test
	void removeKeepsPricingHeldTicker() {
		service.add("AAPL").block();
		db.insertPosition("AAPL", 5, 190);

		service.remove("AAPL").block();

		simulator.tick();
		assertThat(cache.get("AAPL")).isNotNull();
	}

	@Test
	void removeOfAbsentTickerIsRejected() {
		StepVerifier.create(service.remove("AAPL"))
				.expectError(TickerNotWatchedException.class)
				.verify();
	}
}
