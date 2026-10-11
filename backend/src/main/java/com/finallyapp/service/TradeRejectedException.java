package com.finallyapp.service;

/** A trade broke a business rule (no price, insufficient cash or shares, bad quantity). */
public class TradeRejectedException extends DomainException {

	public TradeRejectedException(String message) {
		super(message);
	}
}
