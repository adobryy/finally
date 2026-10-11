package com.finallyapp.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

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

/** Trade and portfolio endpoints against a fresh seeded database. */
@SpringBootTest
@AutoConfigureWebTestClient
@DirtiesContext // closes the SQLite connection so the temp directory can be deleted
class PortfolioControllerTest {

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		registry.add("app.db-path", () -> tempDir.resolve("finally.db").toString());
	}

	@Autowired
	WebTestClient client;

	@Test
	void buyThenSellUpdatesPortfolio() {
		trade("{\"ticker\":\"nvda\",\"quantity\":2,\"side\":\"buy\"}").expectStatus().isOk()
				.expectBody()
				.jsonPath("$.ticker").isEqualTo("NVDA")
				.jsonPath("$.side").isEqualTo("buy")
				.jsonPath("$.quantity").isEqualTo(2.0)
				.jsonPath("$.price").isNumber()
				.jsonPath("$.cash_balance").isNumber();

		client.get().uri("/api/portfolio").exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.cash_balance").isNumber()
				.jsonPath("$.total_value").isNumber()
				.jsonPath("$.unrealized_pnl").isNumber()
				.jsonPath("$.positions[0].ticker").isEqualTo("NVDA")
				.jsonPath("$.positions[0].quantity").isEqualTo(2.0)
				.jsonPath("$.positions[0].avg_cost").isNumber()
				.jsonPath("$.positions[0].current_price").isNumber()
				.jsonPath("$.positions[0].market_value").isNumber()
				.jsonPath("$.positions[0].pnl_percent").isNumber();

		trade("{\"ticker\":\"NVDA\",\"quantity\":2,\"side\":\"sell\"}").expectStatus().isOk()
				.expectBody().jsonPath("$.side").isEqualTo("sell");

		client.get().uri("/api/portfolio").exchange()
				.expectBody().jsonPath("$.positions.length()").isEqualTo(0);
	}

	@Test
	void historyHasPointAfterTrade() {
		trade("{\"ticker\":\"JPM\",\"quantity\":1,\"side\":\"buy\"}").expectStatus().isOk();

		client.get().uri("/api/portfolio/history").exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$[-1].total_value").isNumber()
				.jsonPath("$[-1].recorded_at").value(String.class, t -> assertThat(t).endsWith("Z"));

		trade("{\"ticker\":\"JPM\",\"quantity\":1,\"side\":\"sell\"}").expectStatus().isOk();
	}

	@Test
	void healthIsOk() {
		client.get().uri("/api/health").exchange()
				.expectStatus().isOk()
				.expectBody().json("{\"status\":\"ok\"}");
	}

	@Test
	void rejectedTradeIsProblemDetail() {
		trade("{\"ticker\":\"META\",\"quantity\":1000,\"side\":\"buy\"}").expectStatus().isBadRequest()
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody().jsonPath("$.detail").value(String.class, d -> assertThat(d).startsWith("Insufficient cash"));
	}

	@Test
	void validationErrorsListEveryField() {
		problem(trade("{\"ticker\":\"\",\"quantity\":-1}"),
				"quantity: must be greater than 0; side: must not be null; ticker: must not be blank");
	}

	@Test
	void unreadableFieldIsNamed() {
		problem(trade("{\"ticker\":\"AAPL\",\"quantity\":1,\"side\":\"hold\"}"), "Invalid value for field 'side'");
		problem(trade("{\"ticker\":\"AAPL\",\"quantity\":\"abc\",\"side\":\"buy\"}"),
				"Invalid value for field 'quantity'");
	}

	@Test
	void malformedOrMissingBodyIsBadRequest() {
		problem(trade("{\"ticker\":"), "Malformed JSON request body");
		problem(client.post().uri("/api/portfolio/trade").contentType(MediaType.APPLICATION_JSON).exchange(),
				"No request body");
	}

	private static void problem(WebTestClient.ResponseSpec response, String detail) {
		response.expectStatus().isBadRequest()
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody().jsonPath("$.detail").isEqualTo(detail);
	}

	private WebTestClient.ResponseSpec trade(String json) {
		return client.post().uri("/api/portfolio/trade")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(json)
				.exchange();
	}
}
