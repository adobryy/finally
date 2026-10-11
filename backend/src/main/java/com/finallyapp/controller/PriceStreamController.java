package com.finallyapp.controller;

import java.time.Duration;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finallyapp.model.PriceQuote;
import com.finallyapp.service.MarketService;

import reactor.core.publisher.Flux;

/** Server-sent stream of all live quotes. */
@RestController
public class PriceStreamController {

	private static final Duration RETRY = Duration.ofSeconds(1);

	private final MarketService marketService;

	public PriceStreamController(MarketService marketService) {
		this.marketService = marketService;
	}

	@GetMapping(path = "/api/stream/prices", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<List<PriceQuote>>> prices() {
		return marketService.priceStream()
				.map(quotes -> ServerSentEvent.builder(quotes).retry(RETRY).build());
	}
}
