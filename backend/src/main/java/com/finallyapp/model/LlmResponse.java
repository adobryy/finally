package com.finallyapp.model;

import java.util.List;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Structured assistant output, matching {@code chat/response-schema.json}. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record LlmResponse(String message, List<LlmTrade> trades, List<LlmWatchlistChange> watchlistChanges) {

	public LlmResponse {
		trades = trades == null ? List.of() : trades;
		watchlistChanges = watchlistChanges == null ? List.of() : watchlistChanges;
	}
}
