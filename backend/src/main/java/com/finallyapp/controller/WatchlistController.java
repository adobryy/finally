package com.finallyapp.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.finallyapp.controller.dto.AddWatchlistRequest;
import com.finallyapp.model.WatchlistItem;
import com.finallyapp.service.WatchlistService;

import reactor.core.publisher.Mono;

/** Watchlist CRUD. */
@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

	private final WatchlistService watchlistService;

	public WatchlistController(WatchlistService watchlistService) {
		this.watchlistService = watchlistService;
	}

	@GetMapping
	public Mono<List<WatchlistItem>> list() {
		return watchlistService.list();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Mono<WatchlistItem> add(@Validated @RequestBody AddWatchlistRequest request) {
		return watchlistService.add(request.ticker());
	}

	@DeleteMapping("/{ticker}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public Mono<Void> remove(@PathVariable String ticker) {
		return watchlistService.remove(ticker);
	}
}
