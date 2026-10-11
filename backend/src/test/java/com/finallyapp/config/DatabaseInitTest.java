package com.finallyapp.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.finallyapp.FinallyApplication;

/** Starts the application repeatedly on one database file, as a restart would. */
class DatabaseInitTest {

	@TempDir
	Path tempDir;

	@Test
	void createsMissingDirectoryAndSeedsFreshDatabase() {
		Path dbFile = tempDir.resolve("nested/dir/finally.db");

		start(dbFile, jdbc -> {
			assertThat(watchlist(jdbc)).containsExactly(
					"AAPL", "AMZN", "GOOGL", "JPM", "META", "MSFT", "NFLX", "NVDA", "TSLA", "V");
			assertThat(cash(jdbc)).isEqualTo(10000.0);
		});

		assertThat(dbFile).exists();
	}

	@Test
	void secondStartAddsNoDuplicates() {
		Path dbFile = tempDir.resolve("finally.db");

		start(dbFile, jdbc -> { });
		start(dbFile, jdbc -> {
			assertThat(watchlist(jdbc)).hasSize(10);
			assertThat(jdbc.sql("SELECT count(*) FROM users_profile").query(Integer.class).single()).isEqualTo(1);
			assertThat(cash(jdbc)).isEqualTo(10000.0);
		});
	}

	@Test
	void userChangesSurviveRestart() {
		Path dbFile = tempDir.resolve("finally.db");

		start(dbFile, jdbc -> {
			jdbc.sql("DELETE FROM watchlist WHERE ticker = 'TSLA'").update();
			jdbc.sql("UPDATE users_profile SET cash_balance = 5000.0").update();
		});
		start(dbFile, jdbc -> {
			assertThat(watchlist(jdbc)).hasSize(9).doesNotContain("TSLA");
			assertThat(cash(jdbc)).isEqualTo(5000.0);
		});
	}

	@Test
	void createsAllTables() {
		start(tempDir.resolve("finally.db"), jdbc -> assertThat(
				jdbc.sql("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name").query(String.class).list())
				.containsExactly("chat_messages", "portfolio_snapshots", "positions", "trades", "users_profile", "watchlist"));
	}

	private static void start(Path dbFile, Consumer<JdbcClient> check) {
		try (ConfigurableApplicationContext context = SpringApplication.run(FinallyApplication.class,
				"--app.db-path=" + dbFile, "--spring.main.web-application-type=none")) {
			check.accept(context.getBean(JdbcClient.class));
		}
	}

	private static List<String> watchlist(JdbcClient jdbc) {
		return jdbc.sql("SELECT ticker FROM watchlist ORDER BY ticker").query(String.class).list();
	}

	private static double cash(JdbcClient jdbc) {
		return jdbc.sql("SELECT cash_balance FROM users_profile WHERE id = 'default'").query(Double.class).single();
	}
}
