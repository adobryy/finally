package com.finallyapp.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WatchlistRepositoryTest {

	@TempDir
	Path tempDir;

	private TestDatabase db;
	private WatchlistRepository repository;

	@BeforeEach
	void setUp() {
		db = TestDatabase.create(tempDir);
		repository = new WatchlistRepository(db.jdbc());
	}

	@AfterEach
	void tearDown() {
		db.close();
	}

	@Test
	void addKeepsInsertionOrder() {
		assertThat(repository.add("default", "MSFT")).isTrue();
		assertThat(repository.add("default", "AAPL")).isTrue();

		assertThat(repository.findTickers("default")).containsExactly("MSFT", "AAPL");
	}

	@Test
	void duplicateAddReturnsFalse() {
		repository.add("default", "AAPL");

		assertThat(repository.add("default", "AAPL")).isFalse();
		assertThat(repository.findTickers("default")).containsExactly("AAPL");
	}

	@Test
	void removeReportsWhetherTickerExisted() {
		repository.add("default", "AAPL");

		assertThat(repository.remove("default", "AAPL")).isTrue();
		assertThat(repository.remove("default", "AAPL")).isFalse();
		assertThat(repository.contains("default", "AAPL")).isFalse();
	}

	@Test
	void usersAreIsolated() {
		repository.add("default", "AAPL");
		repository.add("other", "AAPL");

		assertThat(repository.findTickers("other")).containsExactly("AAPL");
		assertThat(repository.remove("other", "AAPL")).isTrue();
		assertThat(repository.contains("default", "AAPL")).isTrue();
	}
}
