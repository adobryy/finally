package com.finallyapp.repository.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.finallyapp.repository.client.MassiveSnapshot.TickerSnapshot;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import reactor.test.StepVerifier;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class MassiveClientTest {

	private final MockWebServer server = new MockWebServer();
	private MassiveClient client;

	@BeforeEach
	void setUp() throws IOException {
		server.start();
		client = new MassiveClient(snakeCaseWebClient(server.url("/").toString()), "test-key");
	}

	@AfterEach
	void tearDown() {
		server.close();
	}

	@Test
	void sendsTickersAndApiKeyInOneCall() throws InterruptedException {
		server.enqueue(json("{\"tickers\":[]}"));

		client.snapshots(List.of("AAPL", "MSFT")).block();

		RecordedRequest request = server.takeRequest();
		assertThat(request.getUrl().encodedPath()).isEqualTo(MassiveClient.SNAPSHOT_PATH);
		assertThat(request.getUrl().queryParameter("tickers")).isEqualTo("AAPL,MSFT");
		assertThat(request.getUrl().queryParameter("apiKey")).isEqualTo("test-key");
	}

	@Test
	void parsesRecordedSnapshotDespiteSnakeCaseDefault() throws IOException {
		server.enqueue(json(resource("/massive/snapshot.json")));

		List<TickerSnapshot> snapshots = client.snapshots(List.of("AAPL", "MSFT", "TSLA")).block();

		assertThat(snapshots).extracting(TickerSnapshot::ticker).containsExactly("AAPL", "MSFT", "TSLA");
		TickerSnapshot aapl = snapshots.get(0);
		assertThat(aapl.lastTrade().p()).isEqualTo(190.12);
		assertThat(aapl.prevDay().c()).isEqualTo(189.40);
		assertThat(aapl.price()).isEqualTo(190.12);
		assertThat(aapl.openPrice()).isEqualTo(189.40);
	}

	@Test
	void fallsBackToDayCloseAndHandlesMissingFields() throws IOException {
		server.enqueue(json(resource("/massive/snapshot.json")));

		List<TickerSnapshot> snapshots = client.snapshots(List.of("AAPL", "MSFT", "TSLA")).block();

		TickerSnapshot msft = snapshots.get(1);
		assertThat(msft.lastTrade()).isNull();
		assertThat(msft.price()).isEqualTo(421.37);
		assertThat(msft.openPrice()).isEqualTo(418.0);
		TickerSnapshot tsla = snapshots.get(2);
		assertThat(tsla.price()).isNull();
		assertThat(tsla.openPrice()).isNull();
	}

	@Test
	void missingTickersArrayGivesEmptyList() {
		server.enqueue(json("{\"status\":\"OK\"}"));

		assertThat(client.snapshots(List.of("AAPL")).block()).isEmpty();
	}

	@Test
	void httpErrorIsSignalled() {
		server.enqueue(new MockResponse.Builder().code(429).body("rate limited").build());

		StepVerifier.create(client.snapshots(List.of("AAPL")))
				.expectError(WebClientResponseException.TooManyRequests.class)
				.verify();
	}

	/** A WebClient whose JSON default is snake_case, like the application's. */
	private static WebClient snakeCaseWebClient(String baseUrl) {
		JsonMapper mapper = JsonMapper.builder()
				.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
				.build();
		return WebClient.builder()
				.baseUrl(baseUrl)
				.codecs(codecs -> codecs.defaultCodecs().jacksonJsonDecoder(new JacksonJsonDecoder(mapper)))
				.build();
	}

	static MockResponse json(String body) {
		return new MockResponse.Builder()
				.addHeader("Content-Type", "application/json")
				.body(body)
				.build();
	}

	static String resource(String path) throws IOException {
		try (var in = MassiveClientTest.class.getResourceAsStream(path)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
