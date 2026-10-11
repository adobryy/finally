package com.finallyapp.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed application settings bound from the {@code app.*} properties. */
@ConfigurationProperties("app")
public record AppProperties(Massive massive, OpenRouter openrouter, boolean llmMock, String dbPath) {

	/** Massive (Polygon.io) market data settings; a blank api key selects the simulator. */
	public record Massive(String apiKey, String baseUrl, Duration pollInterval) {

		public boolean enabled() {
			return apiKey != null && !apiKey.isBlank();
		}
	}

	/** OpenRouter chat-completions settings. */
	public record OpenRouter(String apiKey, String baseUrl, String model) {
	}
}
