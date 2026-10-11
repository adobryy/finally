package com.finallyapp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Trade side; lowercase on the wire. */
public enum Side {
	BUY, SELL;

	@JsonValue
	public String wire() {
		return name().toLowerCase();
	}

	@JsonCreator
	public static Side of(String value) {
		return valueOf(value.toUpperCase());
	}
}
