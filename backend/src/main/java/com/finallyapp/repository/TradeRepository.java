package com.finallyapp.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.finallyapp.model.Side;

/** Append-only trade log. */
@Repository
public class TradeRepository {

	private final JdbcClient jdbc;

	public TradeRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public void insert(String userId, String ticker, Side side, double quantity, double price) {
		jdbc.sql("""
				INSERT INTO trades (id, user_id, ticker, side, quantity, price, executed_at)
				VALUES (?, ?, ?, ?, ?, ?, ?)""")
				.params(UUID.randomUUID().toString(), userId, ticker, side.wire(), quantity, price,
						Instant.now().toString())
				.update();
	}
}
