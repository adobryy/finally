package com.finallyapp.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.finallyapp.model.ChatActions;
import com.finallyapp.model.ChatMessage;

import tools.jackson.databind.json.JsonMapper;

/** Chat history per user; actions are stored as JSON. */
@Repository
public class ChatMessageRepository {

	private final JdbcClient jdbc;
	private final JsonMapper mapper;

	public ChatMessageRepository(JdbcClient jdbc, JsonMapper mapper) {
		this.jdbc = jdbc;
		this.mapper = mapper;
	}

	public void insert(String userId, ChatMessage message) {
		String actions = message.actions() == null ? null : mapper.writeValueAsString(message.actions());
		jdbc.sql("""
				INSERT INTO chat_messages (id, user_id, role, content, actions, created_at)
				VALUES (?, ?, ?, ?, ?, ?)""")
				.params(UUID.randomUUID().toString(), userId, message.role(), message.content(), actions,
						message.createdAt().toString())
				.update();
	}

	/** The latest {@code limit} messages, oldest first (rowid is insertion order; ISO strings vary in length). */
	public List<ChatMessage> findRecent(String userId, int limit) {
		return jdbc.sql("""
				SELECT role, content, actions, created_at FROM (
				    SELECT rowid AS seq, * FROM chat_messages WHERE user_id = ? ORDER BY seq DESC LIMIT ?)
				ORDER BY seq""")
				.params(userId, limit)
				.query((rs, row) -> new ChatMessage(rs.getString(1), rs.getString(2), actions(rs.getString(3)),
						Instant.parse(rs.getString(4))))
				.list();
	}

	private ChatActions actions(String json) {
		return json == null ? null : mapper.readValue(json, ChatActions.class);
	}
}
