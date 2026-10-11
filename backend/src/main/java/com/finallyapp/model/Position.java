package com.finallyapp.model;

/** A stored holding of one ticker. */
public record Position(String ticker, double quantity, double avgCost) {
}
