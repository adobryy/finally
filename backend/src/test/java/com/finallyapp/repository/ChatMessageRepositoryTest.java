package com.finallyapp.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.finallyapp.model.ChatActions;
import com.finallyapp.model.ChatMessage;
import com.finallyapp.model.ExecutedTrade;
import com.finallyapp.model.Side;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class ChatMessageRepositoryTest {

	@TempDir
	Path tempDir;

	private final JsonMapper mapper = JsonMapper.builder()
			.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
			.build();
	private TestDatabase db;
	private ChatMessageRepository repository;

	@BeforeEach
	void setUp() {
		db = TestDatabase.create(tempDir);
		repository = new ChatMessageRepository(db.jdbc(), mapper);
	}

	@AfterEach
	void tearDown() {
		db.close();
	}

	@Test
	void storesActionsAsSnakeCaseJson() {
		ChatActions actions = new ChatActions(List.of(new ExecutedTrade("AAPL", Side.BUY, 5, 190.12)), List.of(),
				List.of("oops"));
		Instant at = Instant.parse("2026-10-11T10:00:00.5Z");

		repository.insert("default", new ChatMessage(ChatMessage.ASSISTANT, "Bought", actions, at));

		assertThat(db.jdbc().sql("SELECT actions FROM chat_messages").query(String.class).single())
				.contains("\"watchlist_changes\":[]").contains("\"side\":\"buy\"");
		assertThat(repository.findRecent("default", 10))
				.containsExactly(new ChatMessage(ChatMessage.ASSISTANT, "Bought", actions, at));
	}

	@Test
	void userMessagesHaveNullActions() {
		repository.insert("default", new ChatMessage(ChatMessage.USER, "hi", null, Instant.now()));

		assertThat(repository.findRecent("default", 10).getFirst().actions()).isNull();
	}

	@Test
	void findRecentReturnsLatestOldestFirstEvenWithVaryingTimestampLengths() {
		Instant base = Instant.parse("2026-10-11T10:00:00Z");
		for (int i = 0; i < 5; i++) {
			// i = 0 gives "...00Z", shorter than "...00.000000001Z", which would sort after it as a string.
			repository.insert("default", new ChatMessage(ChatMessage.USER, "m" + i, null, base.plusNanos(i)));
		}

		assertThat(repository.findRecent("default", 3)).extracting(ChatMessage::content)
				.containsExactly("m2", "m3", "m4");
		assertThat(repository.findRecent("default", 10)).extracting(ChatMessage::content)
				.containsExactly("m0", "m1", "m2", "m3", "m4");
	}
}
