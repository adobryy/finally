package com.finallyapp.service;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.finallyapp.model.DefaultUser;
import com.finallyapp.model.PriceQuote;
import com.finallyapp.repository.PositionRepository;
import com.finallyapp.repository.PriceCache;
import com.finallyapp.repository.WatchlistRepository;
import com.finallyapp.service.market.MarketDataSource;

import reactor.core.publisher.Flux;

/** Owns the tracked set (watchlist and open positions) and the live price stream. */
@Service
public class MarketService {

	static final Duration STREAM_INTERVAL = Duration.ofMillis(500);

	private final MarketDataSource source;
	private final PriceCache priceCache;
	private final WatchlistRepository watchlist;
	private final PositionRepository positions;

	public MarketService(MarketDataSource source, PriceCache priceCache,
			WatchlistRepository watchlist, PositionRepository positions) {
		this.source = source;
		this.priceCache = priceCache;
		this.watchlist = watchlist;
		this.positions = positions;
	}

	/** Track every watched or held ticker once the application has started. */
	@EventListener(ApplicationReadyEvent.class)
	public void trackStored() {
		watchlist.findTickers(DefaultUser.ID).forEach(source::track);
		positions.findTickers(DefaultUser.ID).forEach(source::track);
	}

	public void track(String ticker) {
		source.track(ticker);
	}

	/** Stop pricing a ticker that is neither watched nor held. Blocking. */
	public void releaseIfUnused(String ticker) {
		if (!watchlist.contains(DefaultUser.ID, ticker) && !positions.contains(DefaultUser.ID, ticker)) {
			source.untrack(ticker);
			priceCache.remove(ticker);
		}
	}

	/** A snapshot of all cached quotes every 500ms. */
	public Flux<List<PriceQuote>> priceStream() {
		return Flux.interval(Duration.ZERO, STREAM_INTERVAL).map(tick -> priceCache.all());
	}
}
