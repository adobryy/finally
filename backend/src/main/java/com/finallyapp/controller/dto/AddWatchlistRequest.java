package com.finallyapp.controller.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/watchlist}. */
public record AddWatchlistRequest(@NotBlank String ticker) {
}
