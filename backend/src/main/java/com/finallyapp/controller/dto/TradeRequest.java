package com.finallyapp.controller.dto;

import com.finallyapp.model.Side;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Body of {@code POST /api/portfolio/trade}. */
public record TradeRequest(@NotBlank String ticker, @NotNull @Positive Double quantity, @NotNull Side side) {
}
