package com.finallyapp.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.finallyapp.model.Direction;
import com.finallyapp.model.PriceQuote;

class PriceCacheTest {

	private final PriceCache cache = new PriceCache();

	@Test
	void firstUpdateSetsOpenAndPreviousToPrice() {
		PriceQuote quote = cache.update("AAPL", 190.0);

		assertThat(quote.price()).isEqualTo(190.0);
		assertThat(quote.previousPrice()).isEqualTo(190.0);
		assertThat(quote.openPrice()).isEqualTo(190.0);
		assertThat(quote.direction()).isEqualTo(Direction.FLAT);
	}

	@Test
	void laterUpdatesKeepOpenAndTrackPrevious() {
		cache.update("AAPL", 190.0);
		cache.update("AAPL", 191.0);
		PriceQuote quote = cache.update("AAPL", 189.5);

		assertThat(quote.openPrice()).isEqualTo(190.0);
		assertThat(quote.previousPrice()).isEqualTo(191.0);
		assertThat(quote.direction()).isEqualTo(Direction.DOWN);
		assertThat(quote.changePercent()).isEqualTo(-0.26);
	}

	@Test
	void roundsPricesToCents() {
		PriceQuote quote = cache.update("AAPL", 190.12678, 189.40321);

		assertThat(quote.price()).isEqualTo(190.13);
		assertThat(quote.openPrice()).isEqualTo(189.40);
	}

	@Test
	void explicitOpenPriceIsUsed() {
		cache.update("AAPL", 190.0, 180.0);
		PriceQuote quote = cache.update("AAPL", 198.0, 180.0);

		assertThat(quote.previousPrice()).isEqualTo(190.0);
		assertThat(quote.changePercent()).isEqualTo(10.0);
		assertThat(quote.direction()).isEqualTo(Direction.UP);
	}

	@Test
	void getAllAndRemove() {
		cache.update("AAPL", 190.0);
		cache.update("MSFT", 420.0);

		assertThat(cache.get("AAPL").price()).isEqualTo(190.0);
		assertThat(cache.all()).extracting(PriceQuote::ticker).containsExactlyInAnyOrder("AAPL", "MSFT");

		cache.remove("AAPL");

		assertThat(cache.get("AAPL")).isNull();
		assertThat(cache.all()).hasSize(1);
	}
}
