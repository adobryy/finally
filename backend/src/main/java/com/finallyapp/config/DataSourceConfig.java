package com.finallyapp.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.zaxxer.hikari.HikariDataSource;

/** SQLite DataSource; creates the database directory first, since sqlite-jdbc creates only the file. */
@Configuration
public class DataSourceConfig {

	@Bean
	@ConfigurationProperties("spring.datasource.hikari")
	HikariDataSource dataSource(DataSourceProperties dataSource, AppProperties properties) throws IOException {
		Path parent = Path.of(properties.dbPath()).toAbsolutePath().getParent();
		Files.createDirectories(parent);
		return dataSource.initializeDataSourceBuilder().type(HikariDataSource.class).build();
	}
}
