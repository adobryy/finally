package com.finallyapp.service.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.finallyapp.model.PriceQuote;
import com.finallyapp.repository.PriceCache;
import com.finallyapp.repository.client.MassiveClient;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;

class MassiveMarketDataSourceTest {

	private final MockWebServer server = new MockWebServer();
	private final PriceCache cache = new PriceCache();
	private MassiveMarketDataSource source;

	@BeforeEach
	void setUp() throws IOException {
		server.start();
		WebClient webClient = WebClient.builder().baseUrl(server.url("/").toString()).build();
		source = new MassiveMarketDataSource(new MassiveClient(webClient, "test-key"), cache);
	}

	@AfterEach
	void tearDown() {
		server.close();
	}

	@Test
	void pollStoresPriceWithPreviousCloseAsOpen() throws IOException {
		trackAll();
		server.enqueue(snapshot());

		source.poll();

		PriceQuote aapl = cache.get("AAPL");
		assertThat(aapl.price()).isEqualTo(190.12);
		assertThat(aapl.openPrice()).isEqualTo(189.40);
		assertThat(aapl.changePercent()).isEqualTo(0.38);
		assertThat(cache.get("MSFT").price()).isEqualTo(421.37);
	}

	@Test
	void tickerWithoutPriceIsSkipped() throws IOException {
		trackAll();
		server.enqueue(snapshot());

		source.poll();

		assertThat(cache.get("TSLA")).isNull();
	}

	@Test
	void untrackedTickerIsNotStored() throws IOException {
		trackAll();
		source.untrack("MSFT");
		server.enqueue(snapshot());

		source.poll();

		assertThat(cache.get("MSFT")).isNull();
		assertThat(cache.get("AAPL")).isNotNull();
	}

	@Test
	void failedPollKeepsLastKnownPrices() throws IOException {
		trackAll();
		server.enqueue(snapshot());
		source.poll();
		server.enqueue(new MockResponse.Builder().code(500).build());

		source.poll();

		assertThat(cache.get("AAPL").price()).isEqualTo(190.12);
		assertThat(server.getRequestCount()).isEqualTo(2);
	}

	@Test
	void noTrackedTickersMeansNoRequest() {
		source.poll();

		assertThat(server.getRequestCount()).isZero();
	}

	private void trackAll() {
		source.track("AAPL");
		source.track("MSFT");
		source.track("TSLA");
	}

	private static MockResponse snapshot() throws IOException {
		try (var in = MassiveMarketDataSourceTest.class.getResourceAsStream("/massive/snapshot.json")) {
			return new MockResponse.Builder()
					.addHeader("Content-Type", "application/json")
					.body(new String(in.readAllBytes(), StandardCharsets.UTF_8))
					.build();
		}
	}
}
