package com.finallyapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.finallyapp.model.Portfolio;
import com.finallyapp.model.PortfolioPosition;
import com.finallyapp.model.PortfolioSnapshot;
import com.finallyapp.model.Position;
import com.finallyapp.model.Side;
import com.finallyapp.model.TradeResult;
import com.finallyapp.repository.PositionRepository;
import com.finallyapp.repository.PriceCache;
import com.finallyapp.repository.SnapshotRepository;
import com.finallyapp.repository.TestDatabase;
import com.finallyapp.repository.TradeRepository;
import com.finallyapp.repository.UserProfileRepository;
import com.finallyapp.repository.WatchlistRepository;
import com.finallyapp.service.market.SimulatorMarketDataSource;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PortfolioServiceTest {

	@TempDir
	Path tempDir;

	private final PriceCache cache = new PriceCache();
	private final SimulatorMarketDataSource simulator = new SimulatorMarketDataSource(cache, new Random(1));
	private TestDatabase db;
	private PositionRepository positions;
	private PortfolioService service;

	@BeforeEach
	void setUp() {
		db = TestDatabase.create(tempDir);
		db.insertProfile(10000);
		positions = new PositionRepository(db.jdbc());
		MarketService market = new MarketService(simulator, cache, new WatchlistRepository(db.jdbc()), positions);
		service = new PortfolioService(new UserProfileRepository(db.jdbc()), positions,
				new TradeRepository(db.jdbc()), new SnapshotRepository(db.jdbc()), cache, market, db.transactions());
	}

	@AfterEach
	void tearDown() {
		db.close();
	}

	@Test
	void buyDebitsCashAndCreatesPosition() {
		price("AAPL", 190);

		TradeResult result = service.trade("aapl", Side.BUY, 10).block();

		assertThat(result).isEqualTo(new TradeResult("AAPL", Side.BUY, 10, 190, 8100));
		assertThat(positions.find("default", "AAPL")).contains(new Position("AAPL", 10, 190));
		assertThat(cash()).isEqualTo(8100);
	}

	@Test
	void secondBuyAveragesCost() {
		price("AAPL", 100);
		service.trade("AAPL", Side.BUY, 10).block();
		price("AAPL", 130);

		service.trade("AAPL", Side.BUY, 5).block();

		assertThat(positions.find("default", "AAPL")).contains(new Position("AAPL", 15, 110));
		assertThat(cash()).isEqualTo(10000 - 1000 - 650);
	}

	@Test
	void fractionalSharesAreSupported() {
		price("AAPL", 200);

		service.trade("AAPL", Side.BUY, 0.25).block();
		service.trade("AAPL", Side.SELL, 0.1).block();

		assertThat(positions.find("default", "AAPL").orElseThrow().quantity()).isCloseTo(0.15, within(1e-9));
		assertThat(cash()).isCloseTo(10000 - 50 + 20, within(1e-9));
	}

	@Test
	void partialSellKeepsAvgCostAndCreditsCash() {
		price("AAPL", 100);
		service.trade("AAPL", Side.BUY, 10).block();
		price("AAPL", 120);

		TradeResult result = service.trade("AAPL", Side.SELL, 4).block();

		assertThat(result).isEqualTo(new TradeResult("AAPL", Side.SELL, 4, 120, 9480));
		assertThat(positions.find("default", "AAPL")).contains(new Position("AAPL", 6, 100));
	}

	@Test
	void sellingAtALossCreditsLessCash() {
		price("AAPL", 100);
		service.trade("AAPL", Side.BUY, 10).block();
		price("AAPL", 80);

		service.trade("AAPL", Side.SELL, 10).block();

		assertThat(cash()).isEqualTo(9800);
	}

	@Test
	void sellAllDeletesPositionEvenWithFloatingPointResidue() {
		price("AAPL", 100);
		service.trade("AAPL", Side.BUY, 0.1).block();
		service.trade("AAPL", Side.BUY, 0.2).block();

		service.trade("AAPL", Side.SELL, 0.3).block();

		assertThat(positions.find("default", "AAPL")).isEmpty();
	}

	@Test
	void insufficientCashIsRejectedAndChangesNothing() {
		price("MSFT", 420);

		StepVerifier.create(service.trade("MSFT", Side.BUY, 100))
				.expectErrorSatisfies(e -> assertThat(e).isInstanceOf(TradeRejectedException.class)
						.hasMessage("Insufficient cash to buy 100 MSFT: need $42000.00, have $10000.00"))
				.verify();
		assertThat(cash()).isEqualTo(10000);
		assertThat(positions.findAll("default")).isEmpty();
		assertThat(tradeCount()).isZero();
	}

	@Test
	void spendingAllCashIsAllowed() {
		price("AAPL", 100);

		service.trade("AAPL", Side.BUY, 100).block();

		assertThat(cash()).isZero();
	}

	@Test
	void insufficientSharesIsRejected() {
		price("TSLA", 250);
		service.trade("TSLA", Side.BUY, 2).block();

		StepVerifier.create(service.trade("TSLA", Side.SELL, 3))
				.expectErrorSatisfies(e -> assertThat(e).isInstanceOf(TradeRejectedException.class)
						.hasMessage("Insufficient shares to sell 3 TSLA: own 2"))
				.verify();
		assertThat(positions.find("default", "TSLA").orElseThrow().quantity()).isEqualTo(2);
	}

	@Test
	void tickerWithoutPriceIsRejected() {
		StepVerifier.create(service.trade("ZZZZ", Side.BUY, 1))
				.expectErrorSatisfies(e -> assertThat(e).isInstanceOf(TradeRejectedException.class)
						.hasMessage("No price available for ZZZZ yet"))
				.verify();
	}

	@Test
	void invalidInputIsRejected() {
		price("AAPL", 190);

		StepVerifier.create(service.trade("AAPL", Side.BUY, 0)).expectError(TradeRejectedException.class).verify();
		StepVerifier.create(service.trade("AAPL", Side.BUY, -1)).expectError(TradeRejectedException.class).verify();
		StepVerifier.create(service.trade("AAPL", Side.BUY, Double.NaN)).expectError(TradeRejectedException.class).verify();
		StepVerifier.create(service.trade("AAPL", null, 1)).expectError(TradeRejectedException.class).verify();
		StepVerifier.create(service.trade("BAD1", Side.BUY, 1)).expectError(InvalidTickerException.class).verify();
	}

	@Test
	void everyTradeIsLogged() {
		price("AAPL", 190);

		service.trade("AAPL", Side.BUY, 2).block();
		service.trade("AAPL", Side.SELL, 1).block();

		assertThat(db.jdbc().sql("SELECT side FROM trades ORDER BY executed_at, rowid").query(String.class).list())
				.containsExactly("buy", "sell");
	}

	@Test
	void concurrentBuysCannotBothSpendTheSameCash() {
		price("AAPL", 100);

		List<Boolean> outcomes = Flux.range(0, 2)
				.flatMap(i -> service.trade("AAPL", Side.BUY, 60).map(r -> true).onErrorResume(e -> Mono.just(false)))
				.collectList()
				.block();

		assertThat(outcomes).containsExactlyInAnyOrder(true, false);
		assertThat(cash()).isEqualTo(4000);
	}

	@Test
	void closingUnwatchedPositionStopsPricing() {
		price("PYPL", 60);
		service.trade("PYPL", Side.BUY, 1).block();

		service.trade("PYPL", Side.SELL, 1).block();

		assertThat(cache.get("PYPL")).isNull();
	}

	@Test
	void portfolioValuesPricedPositionsAndNullsUnpriced() {
		price("AAPL", 100);
		service.trade("AAPL", Side.BUY, 10).block();
		price("AAPL", 110);
		db.insertPosition("ZZZZ", 3, 50);

		Portfolio portfolio = service.portfolio().block();

		assertThat(portfolio.cashBalance()).isEqualTo(9000);
		assertThat(portfolio.totalValue()).isEqualTo(10100);
		assertThat(portfolio.unrealizedPnl()).isEqualTo(100);
		assertThat(portfolio.positions()).containsExactly(
				new PortfolioPosition("AAPL", 10, 100, 110.0, 1100.0, 100.0, 10.0),
				new PortfolioPosition("ZZZZ", 3, 50, null, null, null, null));
	}

	@Test
	void tradeRecordsSnapshotImmediately() {
		price("AAPL", 100);

		service.trade("AAPL", Side.BUY, 10).block();

		assertThat(service.history().block()).extracting(PortfolioSnapshot::totalValue).containsExactly(10000.0);
	}

	@Test
	void rejectedTradeRecordsNoSnapshot() {
		StepVerifier.create(service.trade("ZZZZ", Side.BUY, 1)).expectError().verify();

		assertThat(service.history().block()).isEmpty();
	}

	@Test
	void snapshotIsSkippedWhileAPositionHasNoPrice() {
		db.insertPosition("ZZZZ", 3, 50);

		service.recordSnapshot();

		assertThat(service.history().block()).isEmpty();
	}

	@Test
	void historyCoversLast24HoursOldestFirst() {
		SnapshotRepository snapshots = new SnapshotRepository(db.jdbc());
		Instant now = Instant.now();
		snapshots.insert("default", 9000, now.minus(Duration.ofHours(25)));
		snapshots.insert("default", 9500, now.minus(Duration.ofHours(2)));
		snapshots.insert("default", 9700, now.minus(Duration.ofHours(1)));

		service.recordSnapshot();

		assertThat(service.history().block()).extracting(PortfolioSnapshot::totalValue)
				.containsExactly(9500.0, 9700.0, 10000.0);
	}

	private void price(String ticker, double price) {
		simulator.track(ticker);
		cache.update(ticker, price);
	}

	private double cash() {
		return db.jdbc().sql("SELECT cash_balance FROM users_profile").query(Double.class).single();
	}

	private int tradeCount() {
		return db.jdbc().sql("SELECT count(*) FROM trades").query(Integer.class).single();
	}
}
