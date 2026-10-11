package com.finallyapp.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.finallyapp.model.PriceQuote;
import com.finallyapp.repository.PositionRepository;
import com.finallyapp.repository.PriceCache;
import com.finallyapp.repository.TestDatabase;
import com.finallyapp.repository.WatchlistRepository;
import com.finallyapp.service.market.SimulatorMarketDataSource;

import reactor.test.StepVerifier;

class MarketServiceTest {

	@TempDir
	Path tempDir;

	private final PriceCache cache = new PriceCache();
	private TestDatabase db;
	private WatchlistRepository watchlist;
	private MarketService service;

	@BeforeEach
	void setUp() {
		db = TestDatabase.create(tempDir);
		watchlist = new WatchlistRepository(db.jdbc());
		service = new MarketService(new SimulatorMarketDataSource(cache, new Random(1)), cache,
				watchlist, new PositionRepository(db.jdbc()));
	}

	@AfterEach
	void tearDown() {
		db.close();
	}

	@Test
	void trackStoredCoversWatchlistAndPositions() {
		watchlist.add("default", "AAPL");
		db.insertPosition("TSLA", 2, 250);

		service.trackStored();

		assertThat(cache.all()).extracting(PriceQuote::ticker).containsExactlyInAnyOrder("AAPL", "TSLA");
	}

	@Test
	void releaseIfUnusedKeepsWatchedOrHeldTickers() {
		watchlist.add("default", "AAPL");
		db.insertPosition("TSLA", 2, 250);
		service.trackStored();
		service.track("PYPL");

		service.releaseIfUnused("AAPL");
		service.releaseIfUnused("TSLA");
		service.releaseIfUnused("PYPL");

		assertThat(cache.all()).extracting(PriceQuote::ticker).containsExactlyInAnyOrder("AAPL", "TSLA");
	}

	@Test
	void priceStreamEmitsCacheSnapshots() {
		service.track("AAPL");

		StepVerifier.create(service.priceStream().take(2))
				.assertNext(quotes -> assertThat(quotes).extracting(PriceQuote::ticker).containsExactly("AAPL"))
				.assertNext(quotes -> assertThat(quotes).hasSize(1))
				.verifyComplete();
	}

	@Test
	void priceStreamIsEmptyListWithoutTickers() {
		StepVerifier.create(service.priceStream().take(1))
				.expectNext(List.of())
				.verifyComplete();
	}
}
