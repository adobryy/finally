package com.finallyapp.service.market;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import com.finallyapp.repository.PriceCache;
import com.finallyapp.repository.client.MassiveClient;
import com.finallyapp.repository.client.MassiveSnapshot.TickerSnapshot;

/** Polls Massive snapshots for all tracked tickers; failures keep the last known prices. */
public class MassiveMarketDataSource implements MarketDataSource {

	private static final Logger log = LoggerFactory.getLogger(MassiveMarketDataSource.class);

	private final MassiveClient client;
	private final PriceCache priceCache;
	private final Set<String> tracked = ConcurrentHashMap.newKeySet();

	public MassiveMarketDataSource(MassiveClient client, PriceCache priceCache) {
		this.client = client;
		this.priceCache = priceCache;
	}

	@Override
	public void track(String ticker) {
		tracked.add(ticker);
	}

	@Override
	public void untrack(String ticker) {
		tracked.remove(ticker);
	}

	/** Fetch all tracked tickers in one call and store their prices. */
	@Scheduled(fixedDelayString = "${app.massive.poll-interval}")
	public void poll() {
		if (tracked.isEmpty()) {
			return;
		}
		client.snapshots(List.copyOf(tracked))
				.doOnNext(snapshots -> snapshots.forEach(this::store))
				.doOnError(e -> log.warn("Massive poll failed: {}", e.toString()))
				.onErrorComplete()
				.block();
	}

	private void store(TickerSnapshot snapshot) {
		Double price = snapshot.price();
		if (price == null || !tracked.contains(snapshot.ticker())) {
			return;
		}
		Double open = snapshot.openPrice();
		if (open == null) {
			priceCache.update(snapshot.ticker(), price);
		} else {
			priceCache.update(snapshot.ticker(), price, open);
		}
	}
}
