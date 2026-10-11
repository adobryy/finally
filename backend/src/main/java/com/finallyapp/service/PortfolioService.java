package com.finallyapp.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.finallyapp.model.DefaultUser;
import com.finallyapp.model.Portfolio;
import com.finallyapp.model.PortfolioPosition;
import com.finallyapp.model.PortfolioSnapshot;
import com.finallyapp.model.Position;
import com.finallyapp.model.PriceQuote;
import com.finallyapp.model.Side;
import com.finallyapp.model.TradeResult;
import com.finallyapp.repository.PositionRepository;
import com.finallyapp.repository.PriceCache;
import com.finallyapp.repository.SnapshotRepository;
import com.finallyapp.repository.TradeRepository;
import com.finallyapp.repository.UserProfileRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Market-order trading and portfolio valuation. */
@Service
public class PortfolioService {

	static final double EPSILON = 1e-9;
	static final Duration HISTORY_WINDOW = Duration.ofHours(24);

	private final UserProfileRepository profiles;
	private final PositionRepository positions;
	private final TradeRepository trades;
	private final SnapshotRepository snapshots;
	private final PriceCache priceCache;
	private final MarketService marketService;
	private final TransactionTemplate transactions;

	public PortfolioService(UserProfileRepository profiles, PositionRepository positions, TradeRepository trades,
			SnapshotRepository snapshots, PriceCache priceCache, MarketService marketService, TransactionTemplate transactions) {
		this.profiles = profiles;
		this.positions = positions;
		this.trades = trades;
		this.snapshots = snapshots;
		this.priceCache = priceCache;
		this.marketService = marketService;
		this.transactions = transactions;
	}

	/** Cash and positions valued at the latest prices. */
	public Mono<Portfolio> portfolio() {
		return Mono.fromCallable(this::loadPortfolio).subscribeOn(Schedulers.boundedElastic());
	}

	/** Execute a market order at the current cached price. */
	public Mono<TradeResult> trade(String rawTicker, Side side, double quantity) {
		return Mono.fromCallable(() -> execute(Tickers.normalize(rawTicker), side, quantity))
				.subscribeOn(Schedulers.boundedElastic());
	}

	/** Portfolio values of the last 24 hours, oldest first. */
	public Mono<List<PortfolioSnapshot>> history() {
		return Mono.fromCallable(() -> snapshots.findSince(DefaultUser.ID, Instant.now().minus(HISTORY_WINDOW)))
				.subscribeOn(Schedulers.boundedElastic());
	}

	/** Store the current total value, unless a held position has no price yet. Blocking. */
	public void recordSnapshot() {
		Portfolio portfolio = loadPortfolio();
		if (portfolio.positions().stream().allMatch(PortfolioPosition::priced)) {
			snapshots.insert(DefaultUser.ID, portfolio.totalValue(), Instant.now());
		}
	}

	private Portfolio loadPortfolio() {
		double cash = profiles.findCash(DefaultUser.ID);
		List<PortfolioPosition> valued = positions.findAll(DefaultUser.ID).stream()
				.map(p -> PortfolioPosition.of(p, priceCache.get(p.ticker())))
				.toList();
		return Portfolio.of(cash, valued);
	}

	private TradeResult execute(String ticker, Side side, double quantity) {
		if (side == null) {
			throw new TradeRejectedException("Side must be buy or sell");
		}
		if (!(quantity > 0) || Double.isInfinite(quantity)) {
			throw new TradeRejectedException("Quantity must be greater than 0");
		}
		PriceQuote quote = priceCache.get(ticker);
		if (quote == null) {
			throw new TradeRejectedException("No price available for " + ticker + " yet");
		}
		double price = quote.price();
		TradeResult result = transactions.execute(status -> side == Side.BUY
				? buy(ticker, quantity, price)
				: sell(ticker, quantity, price));
		if (side == Side.BUY) {
			marketService.track(ticker);
		} else {
			marketService.releaseIfUnused(ticker);
		}
		recordSnapshot();
		return result;
	}

	private TradeResult buy(String ticker, double quantity, double price) {
		double cash = profiles.findCash(DefaultUser.ID);
		double cost = quantity * price;
		if (cash + EPSILON < cost) {
			throw new TradeRejectedException("Insufficient cash to buy %s %s: need $%.2f, have $%.2f"
					.formatted(format(quantity), ticker, cost, cash));
		}
		Position old = positions.find(DefaultUser.ID, ticker).orElse(new Position(ticker, 0, 0));
		double newQuantity = old.quantity() + quantity;
		double newAvg = (old.quantity() * old.avgCost() + cost) / newQuantity;
		positions.save(DefaultUser.ID, new Position(ticker, newQuantity, newAvg));
		return record(ticker, Side.BUY, quantity, price, cash - cost);
	}

	private TradeResult sell(String ticker, double quantity, double price) {
		Position held = positions.find(DefaultUser.ID, ticker).orElse(new Position(ticker, 0, 0));
		if (held.quantity() + EPSILON < quantity) {
			throw new TradeRejectedException("Insufficient shares to sell %s %s: own %s"
					.formatted(format(quantity), ticker, format(held.quantity())));
		}
		double remaining = held.quantity() - quantity;
		if (remaining < EPSILON) {
			positions.delete(DefaultUser.ID, ticker);
		} else {
			positions.save(DefaultUser.ID, new Position(ticker, remaining, held.avgCost()));
		}
		double cash = profiles.findCash(DefaultUser.ID) + quantity * price;
		return record(ticker, Side.SELL, quantity, price, cash);
	}

	private TradeResult record(String ticker, Side side, double quantity, double price, double cash) {
		profiles.updateCash(DefaultUser.ID, cash);
		trades.insert(DefaultUser.ID, ticker, side, quantity, price);
		return new TradeResult(ticker, side, quantity, price, cash);
	}

	private static String format(double quantity) {
		return quantity == Math.rint(quantity) ? String.valueOf((long) quantity) : String.valueOf(quantity);
	}
}
