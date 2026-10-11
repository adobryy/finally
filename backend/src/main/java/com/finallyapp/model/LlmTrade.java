package com.finallyapp.model;

/** A trade the assistant wants to execute. */
public record LlmTrade(String ticker, Side side, double quantity) {
}
