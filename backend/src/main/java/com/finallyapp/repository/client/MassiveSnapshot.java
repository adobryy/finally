package com.finallyapp.repository.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Massive snapshot response; Massive JSON is camelCase, unlike the app's snake_case default. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MassiveSnapshot(List<TickerSnapshot> tickers) {

	@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record TickerSnapshot(String ticker, Bar day, Bar prevDay, LastTrade lastTrade) {

		/** Last trade price, falling back to the day's close; null when neither is known. */
		public Double price() {
			if (lastTrade != null && positive(lastTrade.p())) {
				return lastTrade.p();
			}
			return day != null && positive(day.c()) ? day.c() : null;
		}

		/** Previous day's close; null when unknown. */
		public Double openPrice() {
			return prevDay != null && positive(prevDay.c()) ? prevDay.c() : null;
		}

		private static boolean positive(Double value) {
			return value != null && value > 0;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Bar(Double c) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record LastTrade(Double p) {
	}
}
