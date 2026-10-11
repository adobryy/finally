package com.finallyapp.repository.client;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

import org.springframework.web.reactive.function.client.WebClient;

import com.finallyapp.repository.client.MassiveSnapshot.TickerSnapshot;

import reactor.core.publisher.Mono;

/** Massive (Polygon.io) REST client for stock snapshots. */
public class MassiveClient {

	static final String SNAPSHOT_PATH = "/v2/snapshot/locale/us/markets/stocks/tickers";
	static final Duration TIMEOUT = Duration.ofSeconds(10);

	private final WebClient webClient;
	private final String apiKey;

	public MassiveClient(WebClient webClient, String apiKey) {
		this.webClient = webClient;
		this.apiKey = apiKey;
	}

	/** Fetch the snapshots of the given tickers in one call. */
	public Mono<List<TickerSnapshot>> snapshots(Collection<String> tickers) {
		return webClient.get()
				.uri(uri -> uri.path(SNAPSHOT_PATH)
						.queryParam("tickers", String.join(",", tickers))
						.queryParam("apiKey", apiKey)
						.build())
				.retrieve()
				.bodyToMono(MassiveSnapshot.class)
				.map(response -> response.tickers() == null ? List.<TickerSnapshot>of() : response.tickers())
				.timeout(TIMEOUT);
	}
}
