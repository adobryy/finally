package com.finallyapp.model;

import java.time.Instant;

/** Total portfolio value at a point in time. */
public record PortfolioSnapshot(double totalValue, Instant recordedAt) {
}
