package com.finallyapp.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.finallyapp.model.Position;

/** Current holdings per user, one row per ticker. */
@Repository
public class PositionRepository {

	private final JdbcClient jdbc;

	public PositionRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<Position> findAll(String userId) {
		return jdbc.sql("SELECT ticker, quantity, avg_cost FROM positions WHERE user_id = ? ORDER BY ticker")
				.param(userId)
				.query(Position.class)
				.list();
	}

	public Optional<Position> find(String userId, String ticker) {
		return jdbc.sql("SELECT ticker, quantity, avg_cost FROM positions WHERE user_id = ? AND ticker = ?")
				.params(userId, ticker)
				.query(Position.class)
				.optional();
	}

	public List<String> findTickers(String userId) {
		return findAll(userId).stream().map(Position::ticker).toList();
	}

	public boolean contains(String userId, String ticker) {
		return find(userId, ticker).isPresent();
	}

	/** Insert or replace the holding of a ticker. */
	public void save(String userId, Position position) {
		jdbc.sql("""
				INSERT INTO positions (id, user_id, ticker, quantity, avg_cost, updated_at)
				VALUES (?, ?, ?, ?, ?, ?)
				ON CONFLICT (user_id, ticker) DO UPDATE SET
				    quantity = excluded.quantity, avg_cost = excluded.avg_cost, updated_at = excluded.updated_at""")
				.params(UUID.randomUUID().toString(), userId, position.ticker(), position.quantity(),
						position.avgCost(), Instant.now().toString())
				.update();
	}

	public void delete(String userId, String ticker) {
		jdbc.sql("DELETE FROM positions WHERE user_id = ? AND ticker = ?").params(userId, ticker).update();
	}
}
