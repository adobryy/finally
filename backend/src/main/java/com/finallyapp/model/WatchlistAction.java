package com.finallyapp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Watchlist change requested by the assistant; lowercase on the wire. */
public enum WatchlistAction {
	ADD, REMOVE;

	@JsonValue
	public String wire() {
		return name().toLowerCase();
	}

	@JsonCreator
	public static WatchlistAction of(String value) {
		return valueOf(value.toUpperCase());
	}
}
