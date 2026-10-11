package com.finallyapp.service;

/** The ticker is already on the watchlist. */
public class TickerAlreadyWatchedException extends DomainException {

	public TickerAlreadyWatchedException(String ticker) {
		super(ticker + " is already on the watchlist");
	}
}
