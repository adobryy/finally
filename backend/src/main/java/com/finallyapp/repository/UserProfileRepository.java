package com.finallyapp.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Cash balance per user. */
@Repository
public class UserProfileRepository {

	private final JdbcClient jdbc;

	public UserProfileRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public double findCash(String userId) {
		return jdbc.sql("SELECT cash_balance FROM users_profile WHERE id = ?")
				.param(userId)
				.query(Double.class)
				.single();
	}

	public void updateCash(String userId, double cash) {
		jdbc.sql("UPDATE users_profile SET cash_balance = ? WHERE id = ?").params(cash, userId).update();
	}
}
