package com.finallyapp.service;

/** The ticker is not on the watchlist. */
public class TickerNotWatchedException extends DomainException {

	public TickerNotWatchedException(String ticker) {
		super(ticker + " is not on the watchlist");
	}
}
