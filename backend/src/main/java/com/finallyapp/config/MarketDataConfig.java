package com.finallyapp.config;

import java.util.random.RandomGenerator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import com.finallyapp.repository.PriceCache;
import com.finallyapp.repository.client.MassiveClient;
import com.finallyapp.service.market.MarketDataSource;
import com.finallyapp.service.market.MassiveMarketDataSource;
import com.finallyapp.service.market.SimulatorMarketDataSource;

/** Selects the market data source: Massive when an api key is set, otherwise the simulator. */
@Configuration
public class MarketDataConfig {

	@Bean
	MarketDataSource marketDataSource(AppProperties properties, PriceCache priceCache, WebClient.Builder webClient) {
		AppProperties.Massive massive = properties.massive();
		if (massive.enabled()) {
			MassiveClient client = new MassiveClient(webClient.baseUrl(massive.baseUrl()).build(), massive.apiKey());
			return new MassiveMarketDataSource(client, priceCache);
		}
		return new SimulatorMarketDataSource(priceCache, RandomGenerator.getDefault());
	}
}
