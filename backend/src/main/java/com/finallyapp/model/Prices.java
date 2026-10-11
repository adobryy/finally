package com.finallyapp.model;

/** Price rounding shared by the cache and the DTOs. */
public final class Prices {

	private Prices() {
	}

	public static double round2(double value) {
		return Math.round(value * 100) / 100.0;
	}
}
