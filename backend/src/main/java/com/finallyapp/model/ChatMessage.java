package com.finallyapp.model;

import java.time.Instant;

/** A stored chat message; actions are null for user messages. */
public record ChatMessage(String role, String content, ChatActions actions, Instant createdAt) {

	public static final String USER = "user";
	public static final String ASSISTANT = "assistant";
}
