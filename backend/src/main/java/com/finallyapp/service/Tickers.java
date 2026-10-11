package com.finallyapp.service;

import java.util.regex.Pattern;

/** Ticker normalization shared by watchlist, trades and chat actions. */
public final class Tickers {

	private static final Pattern VALID = Pattern.compile("^[A-Z.]{1,10}$");

	private Tickers() {
	}

	/** Trim and upper-case, then require 1-10 letters or dots. */
	public static String normalize(String raw) {
		String ticker = raw == null ? "" : raw.trim().toUpperCase();
		if (!VALID.matcher(ticker).matches()) {
			throw new InvalidTickerException(raw);
		}
		return ticker;
	}
}
