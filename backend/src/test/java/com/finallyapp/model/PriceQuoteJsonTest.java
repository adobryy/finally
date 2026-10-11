package com.finallyapp.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class PriceQuoteJsonTest {

	private final JsonMapper mapper = JsonMapper.builder()
			.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
			.build();

	@Test
	void serializesToWireContract() {
		PriceQuote quote = new PriceQuote("AAPL", 190.12, 190.05, 189.40,
				Instant.parse("2026-10-06T14:00:00.500Z"));

		JsonNode json = mapper.valueToTree(quote);

		assertThat(json.get("ticker").asString()).isEqualTo("AAPL");
		assertThat(json.get("price").asDouble()).isEqualTo(190.12);
		assertThat(json.get("previous_price").asDouble()).isEqualTo(190.05);
		assertThat(json.get("open_price").asDouble()).isEqualTo(189.40);
		assertThat(json.get("change_percent").asDouble()).isEqualTo(0.38);
		assertThat(json.get("direction").asString()).isEqualTo("up");
		assertThat(json.get("timestamp").asString()).isEqualTo("2026-10-06T14:00:00.500Z");
	}

	@Test
	void directionReadsAnyCase() {
		assertThat(mapper.readValue("\"down\"", Direction.class)).isEqualTo(Direction.DOWN);
		assertThat(mapper.readValue("\"FLAT\"", Direction.class)).isEqualTo(Direction.FLAT);
	}
}
