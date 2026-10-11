package com.finallyapp.service;

import java.util.List;
import java.util.concurrent.Callable;

import org.springframework.stereotype.Service;

import com.finallyapp.model.DefaultUser;
import com.finallyapp.model.WatchlistItem;
import com.finallyapp.repository.PriceCache;
import com.finallyapp.repository.WatchlistRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Watchlist rules; adding tracks the ticker, removing releases it if unused. */
@Service
public class WatchlistService {

	private final WatchlistRepository watchlist;
	private final MarketService marketService;
	private final PriceCache priceCache;

	public WatchlistService(WatchlistRepository watchlist, MarketService marketService, PriceCache priceCache) {
		this.watchlist = watchlist;
		this.marketService = marketService;
		this.priceCache = priceCache;
	}

	public Mono<List<WatchlistItem>> list() {
		return blocking(() -> watchlist.findTickers(DefaultUser.ID).stream().map(this::item).toList());
	}

	/** Add a ticker; fails if invalid or already watched. */
	public Mono<WatchlistItem> add(String rawTicker) {
		return blocking(() -> {
			String ticker = Tickers.normalize(rawTicker);
			if (!watchlist.add(DefaultUser.ID, ticker)) {
				throw new TickerAlreadyWatchedException(ticker);
			}
			marketService.track(ticker);
			return item(ticker);
		});
	}

	/** Remove a ticker; fails if invalid or not watched. */
	public Mono<Void> remove(String rawTicker) {
		return blocking(() -> {
			String ticker = Tickers.normalize(rawTicker);
			if (!watchlist.remove(DefaultUser.ID, ticker)) {
				throw new TickerNotWatchedException(ticker);
			}
			marketService.releaseIfUnused(ticker);
			return null;
		}).then();
	}

	private WatchlistItem item(String ticker) {
		return WatchlistItem.of(ticker, priceCache.get(ticker));
	}

	private static <T> Mono<T> blocking(Callable<T> call) {
		return Mono.fromCallable(call).subscribeOn(Schedulers.boundedElastic());
	}
}
