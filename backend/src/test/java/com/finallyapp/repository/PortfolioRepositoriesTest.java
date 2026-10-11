package com.finallyapp.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.finallyapp.model.Position;
import com.finallyapp.model.Side;

class PortfolioRepositoriesTest {

	@TempDir
	Path tempDir;

	private TestDatabase db;
	private PositionRepository positions;
	private UserProfileRepository profiles;
	private TradeRepository trades;

	@BeforeEach
	void setUp() {
		db = TestDatabase.create(tempDir);
		positions = new PositionRepository(db.jdbc());
		profiles = new UserProfileRepository(db.jdbc());
		trades = new TradeRepository(db.jdbc());
	}

	@AfterEach
	void tearDown() {
		db.close();
	}

	@Test
	void saveInsertsThenUpdatesOneRowPerTicker() {
		positions.save("default", new Position("MSFT", 2, 400));
		positions.save("default", new Position("AAPL", 1.5, 190));
		positions.save("default", new Position("AAPL", 3, 195));

		assertThat(positions.findAll("default")).containsExactly(
				new Position("AAPL", 3, 195), new Position("MSFT", 2, 400));
		assertThat(positions.findTickers("default")).containsExactly("AAPL", "MSFT");
	}

	@Test
	void findAndDelete() {
		positions.save("default", new Position("AAPL", 1, 190));

		assertThat(positions.find("default", "AAPL")).contains(new Position("AAPL", 1, 190));
		assertThat(positions.contains("default", "AAPL")).isTrue();

		positions.delete("default", "AAPL");

		assertThat(positions.find("default", "AAPL")).isEmpty();
		assertThat(positions.contains("default", "AAPL")).isFalse();
	}

	@Test
	void cashReadAndUpdate() {
		db.insertProfile(10000);

		profiles.updateCash("default", 8100.5);

		assertThat(profiles.findCash("default")).isEqualTo(8100.5);
	}

	@Test
	void tradeIsAppendedWithLowercaseSide() {
		trades.insert("default", "AAPL", Side.BUY, 2.5, 190.12);

		Map<String, Object> row = db.jdbc().sql("SELECT * FROM trades").query().singleRow();
		assertThat(row).containsEntry("ticker", "AAPL").containsEntry("side", "buy")
				.containsEntry("quantity", 2.5).containsEntry("price", 190.12)
				.containsEntry("user_id", "default");
		assertThat(row.get("executed_at")).asString().endsWith("Z");
	}
}
