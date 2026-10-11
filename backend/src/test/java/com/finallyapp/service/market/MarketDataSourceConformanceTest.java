package com.finallyapp.service.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Random;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.finallyapp.repository.PriceCache;
import com.finallyapp.repository.client.MassiveClient;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;

/** Both sources populate the PriceCache for a tracked ticker. */
class MarketDataSourceConformanceTest {

	private static final String AAPL_SNAPSHOT = """
			{"tickers":[{"ticker":"AAPL","lastTrade":{"p":190.5},"prevDay":{"c":189.0}}]}""";

	private final PriceCache cache = new PriceCache();
	private final MockWebServer server = new MockWebServer();

	@AfterEach
	void tearDown() {
		server.close();
	}

	@Test
	void simulatorPopulatesCacheOnTrack() {
		SimulatorMarketDataSource simulator = new SimulatorMarketDataSource(cache, new Random(1));

		simulator.track("AAPL");
		simulator.tick();

		assertThat(cache.get("AAPL")).isNotNull();
		assertThat(cache.get("AAPL").price()).isPositive();
	}

	@Test
	void massivePopulatesCacheOnTrackAndPoll() throws IOException {
		server.start();
		server.enqueue(new MockResponse.Builder()
				.addHeader("Content-Type", "application/json")
				.body(AAPL_SNAPSHOT)
				.build());
		WebClient webClient = WebClient.builder().baseUrl(server.url("/").toString()).build();
		MassiveMarketDataSource massive = new MassiveMarketDataSource(new MassiveClient(webClient, "k"), cache);

		massive.track("AAPL");
		massive.poll();

		assertThat(cache.get("AAPL")).isNotNull();
		assertThat(cache.get("AAPL").price()).isEqualTo(190.5);
	}
}
