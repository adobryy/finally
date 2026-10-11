package com.finallyapp.model;

/** Response of {@code POST /api/chat}. */
public record ChatReply(String message, ChatActions actions) {
}
