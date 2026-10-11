package com.finallyapp.controller;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Chat endpoints with the mock LLM against a fresh seeded database. */
@SpringBootTest
@AutoConfigureWebTestClient
@DirtiesContext // closes the SQLite connection so the temp directory can be deleted
class ChatControllerTest {

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		registry.add("app.db-path", () -> tempDir.resolve("finally.db").toString());
	}

	@Autowired
	WebTestClient client;

	@Test
	void chatTradeThenHistory() {
		chat("buy 1 JPM").expectStatus().isOk()
				.expectBody()
				.jsonPath("$.message").isEqualTo("Mock: buying 1 JPM")
				.jsonPath("$.actions.trades[0].ticker").isEqualTo("JPM")
				.jsonPath("$.actions.trades[0].side").isEqualTo("buy")
				.jsonPath("$.actions.trades[0].quantity").isEqualTo(1.0)
				.jsonPath("$.actions.trades[0].price").isNumber()
				.jsonPath("$.actions.watchlist_changes").isEmpty()
				.jsonPath("$.actions.errors").isEmpty();

		client.get().uri("/api/chat/history").exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$[-2].role").isEqualTo("user")
				.jsonPath("$[-2].content").isEqualTo("buy 1 JPM")
				.jsonPath("$[-2].actions").isEqualTo(null)
				.jsonPath("$[-2].created_at").isNotEmpty()
				.jsonPath("$[-1].role").isEqualTo("assistant")
				.jsonPath("$[-1].actions.trades[0].ticker").isEqualTo("JPM");

		client.get().uri("/api/portfolio").exchange()
				.expectBody().jsonPath("$.positions[0].ticker").isEqualTo("JPM");
	}

	@Test
	void failedChatTradeIsStill200() {
		chat("sell 100 TSLA").expectStatus().isOk()
				.expectBody().jsonPath("$.actions.errors[0]").isEqualTo("Insufficient shares to sell 100 TSLA: own 0");
	}

	@Test
	void blankMessageIsBadRequest() {
		chat(" ").expectStatus().isBadRequest()
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody().jsonPath("$.detail").isEqualTo("message: must not be blank");
	}

	private WebTestClient.ResponseSpec chat(String message) {
		return client.post().uri("/api/chat")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of("message", message))
				.exchange();
	}
}
