package com.finallyapp.controller.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/chat}. */
public record ChatRequest(@NotBlank String message) {
}
