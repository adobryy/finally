package com.finallyapp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Price movement since the previous quote; lowercase on the wire. */
public enum Direction {
	UP, DOWN, FLAT;

	@JsonValue
	public String wire() {
		return name().toLowerCase();
	}

	@JsonCreator
	public static Direction of(String value) {
		return valueOf(value.toUpperCase());
	}
}
