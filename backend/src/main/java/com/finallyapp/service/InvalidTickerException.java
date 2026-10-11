package com.finallyapp.service;

/** The ticker does not match {@code ^[A-Z.]{1,10}$} after normalization. */
public class InvalidTickerException extends DomainException {

	public InvalidTickerException(String ticker) {
		super("Invalid ticker '" + ticker + "': expected 1-10 letters or dots");
	}
}
