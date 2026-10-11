package com.finallyapp.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.test.StepVerifier;

/** Watchlist endpoints and the SSE stream against a fresh seeded database. */
@SpringBootTest
@AutoConfigureWebTestClient
@DirtiesContext // closes the SQLite connection so the temp directory can be deleted
class WatchlistControllerTest {

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		registry.add("app.db-path", () -> tempDir.resolve("finally.db").toString());
	}

	@Autowired
	WebTestClient client;

	@Test
	void listsSeededTickersInOrder() {
		client.get().uri("/api/watchlist").exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.length()").isEqualTo(10)
				.jsonPath("$[0].ticker").isEqualTo("AAPL")
				.jsonPath("$[0].price").isNumber()
				.jsonPath("$[0].change_percent").isNumber()
				.jsonPath("$[0].direction").value(String.class, d -> assertThat(d).isIn("up", "down", "flat"))
				.jsonPath("$[9].ticker").isEqualTo("NFLX");
	}

	@Test
	void addThenRemoveTicker() {
		add("pypl").expectStatus().isCreated()
				.expectBody()
				.jsonPath("$.ticker").isEqualTo("PYPL")
				.jsonPath("$.price").isNumber()
				.jsonPath("$.direction").isEqualTo("flat");

		client.delete().uri("/api/watchlist/pypl").exchange().expectStatus().isNoContent();
		client.delete().uri("/api/watchlist/PYPL").exchange()
				.expectStatus().isNotFound()
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody().jsonPath("$.detail").isEqualTo("PYPL is not on the watchlist");
	}

	@Test
	void duplicateAddIsConflict() {
		add("AAPL").expectStatus().isEqualTo(409)
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody().jsonPath("$.detail").isEqualTo("AAPL is already on the watchlist");
	}

	@Test
	void invalidTickerIsBadRequest() {
		add("BAD1").expectStatus().isBadRequest()
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody().jsonPath("$.detail").value(String.class, d -> assertThat(d).contains("BAD1"));
		add("").expectStatus().isBadRequest()
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody().jsonPath("$.detail").isEqualTo("ticker: must not be blank");
	}

	@Test
	void addedTickerAppearsInPriceStream() {
		add("SHOP").expectStatus().isCreated();

		StepVerifier.create(client.get().uri("/api/stream/prices").accept(MediaType.TEXT_EVENT_STREAM)
						.exchange()
						.expectStatus().isOk()
						.returnResult(new ParameterizedTypeReference<ServerSentEvent<List<Map<String, Object>>>>() {
						})
						.getResponseBody())
				.assertNext(event -> {
					assertThat(event.retry()).hasSeconds(1);
					assertThat(event.data()).extracting(q -> q.get("ticker")).contains("AAPL", "SHOP");
					assertThat(event.data().getFirst()).containsKeys(
							"price", "previous_price", "open_price", "change_percent", "direction", "timestamp");
				})
				.thenCancel()
				.verify();

		client.delete().uri("/api/watchlist/SHOP").exchange().expectStatus().isNoContent();
	}

	private WebTestClient.ResponseSpec add(String ticker) {
		return client.post().uri("/api/watchlist")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of("ticker", ticker))
				.exchange();
	}
}
