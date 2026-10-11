package com.finallyapp.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Watched tickers per user, in the order they were added. */
@Repository
public class WatchlistRepository {

	private final JdbcClient jdbc;

	public WatchlistRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<String> findTickers(String userId) {
		return jdbc.sql("SELECT ticker FROM watchlist WHERE user_id = ? ORDER BY rowid")
				.param(userId)
				.query(String.class)
				.list();
	}

	public boolean contains(String userId, String ticker) {
		return jdbc.sql("SELECT count(*) FROM watchlist WHERE user_id = ? AND ticker = ?")
				.params(userId, ticker)
				.query(Integer.class)
				.single() > 0;
	}

	/** Insert the ticker; returns false if it was already watched. */
	public boolean add(String userId, String ticker) {
		return jdbc.sql("INSERT OR IGNORE INTO watchlist (id, user_id, ticker, added_at) VALUES (?, ?, ?, ?)")
				.params(UUID.randomUUID().toString(), userId, ticker, Instant.now().toString())
				.update() == 1;
	}

	/** Delete the ticker; returns false if it was not watched. */
	public boolean remove(String userId, String ticker) {
		return jdbc.sql("DELETE FROM watchlist WHERE user_id = ? AND ticker = ?")
				.params(userId, ticker)
				.update() == 1;
	}
}
