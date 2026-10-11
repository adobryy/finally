package com.finallyapp.repository;

import java.nio.file.Path;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import com.zaxxer.hikari.HikariDataSource;

/** A fresh, unseeded SQLite file with the application schema and a one-connection pool, as in production. */
public record TestDatabase(HikariDataSource dataSource, JdbcClient jdbc, TransactionTemplate transactions)
		implements AutoCloseable {

	public static TestDatabase create(Path dir) {
		HikariDataSource dataSource = new HikariDataSource();
		dataSource.setJdbcUrl("jdbc:sqlite:" + dir.resolve("test.db"));
		dataSource.setMaximumPoolSize(1);
		new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(dataSource);
		return new TestDatabase(dataSource, JdbcClient.create(dataSource),
				new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
	}

	public void insertProfile(double cash) {
		jdbc.sql("INSERT INTO users_profile (id, cash_balance, created_at) VALUES ('default', ?, '')")
				.param(cash)
				.update();
	}

	public void insertPosition(String ticker, double quantity, double avgCost) {
		jdbc.sql("INSERT INTO positions (id, ticker, quantity, avg_cost, updated_at) VALUES (?, ?, ?, ?, '')")
				.params(ticker + "-id", ticker, quantity, avgCost)
				.update();
	}

	/** Closes the pool so the temp directory can be deleted on Windows. */
	@Override
	public void close() {
		dataSource.close();
	}
}
