package com.finallyapp.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.finallyapp.model.PortfolioSnapshot;

/** Portfolio value history per user. */
@Repository
public class SnapshotRepository {

	private final JdbcClient jdbc;

	public SnapshotRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public void insert(String userId, double totalValue, Instant recordedAt) {
		jdbc.sql("INSERT INTO portfolio_snapshots (id, user_id, total_value, recorded_at) VALUES (?, ?, ?, ?)")
				.params(UUID.randomUUID().toString(), userId, totalValue, recordedAt.toString())
				.update();
	}

	/** Snapshots recorded at or after {@code since}, oldest first (rowid is insertion order). */
	public List<PortfolioSnapshot> findSince(String userId, Instant since) {
		return jdbc.sql("""
				SELECT total_value, recorded_at FROM portfolio_snapshots
				WHERE user_id = ? AND recorded_at >= ? ORDER BY rowid""")
				.params(userId, since.toString())
				.query((rs, row) -> new PortfolioSnapshot(rs.getDouble(1), Instant.parse(rs.getString(2))))
				.list();
	}
}
