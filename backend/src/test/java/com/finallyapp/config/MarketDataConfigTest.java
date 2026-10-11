package com.finallyapp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.time.Duration;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.finallyapp.repository.PriceCache;
import com.finallyapp.service.market.MarketDataSource;
import com.finallyapp.service.market.MassiveMarketDataSource;
import com.finallyapp.service.market.SimulatorMarketDataSource;

import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;

class MarketDataConfigTest {

	@Nested
	@SpringBootTest(properties = "app.massive.api-key=")
	class WithoutApiKey {

		@Autowired
		MarketDataSource source;

		@Autowired
		PriceCache cache;

		@Test
		void usesSimulatorAndTicksOnSchedule() {
			assertThat(source).isInstanceOf(SimulatorMarketDataSource.class);

			source.track("NVDA");

			await().atMost(Duration.ofSeconds(5))
					.untilAsserted(() -> assertThat(cache.get("NVDA").price()).isNotEqualTo(120.0));
		}
	}

	@Nested
	@SpringBootTest(properties = {"app.massive.api-key=test-key", "app.massive.poll-interval=200ms"})
	class WithApiKey {

		static final MockWebServer server = new MockWebServer();

		@Autowired
		MarketDataSource source;

		@Autowired
		PriceCache cache;

		@BeforeAll
		static void startServer() throws IOException {
			server.setDispatcher(new Dispatcher() {
				@Override
				public @NonNull MockResponse dispatch(@NonNull RecordedRequest request) {
					return new MockResponse.Builder()
							.addHeader("Content-Type", "application/json")
							.body("{\"tickers\":[{\"ticker\":\"AAPL\",\"lastTrade\":{\"p\":191.25},\"prevDay\":{\"c\":190.0}}]}")
							.build();
				}
			});
			server.start();
		}

		@AfterAll
		static void stopServer() {
			server.close();
		}

		@DynamicPropertySource
		static void massiveUrl(DynamicPropertyRegistry registry) {
			registry.add("app.massive.base-url", () -> server.url("/").toString());
		}

		@Test
		void usesMassiveAndPollsOnSchedule() {
			assertThat(source).isInstanceOf(MassiveMarketDataSource.class);

			source.track("AAPL");

			await().atMost(Duration.ofSeconds(5))
					.untilAsserted(() -> assertThat(cache.get("AAPL")).isNotNull());
			assertThat(cache.get("AAPL").price()).isEqualTo(191.25);
			assertThat(cache.get("AAPL").openPrice()).isEqualTo(190.0);
		}
	}
}
