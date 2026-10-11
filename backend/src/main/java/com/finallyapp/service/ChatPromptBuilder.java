package com.finallyapp.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.finallyapp.model.ChatMessage;
import com.finallyapp.model.Portfolio;
import com.finallyapp.model.WatchlistItem;
import com.finallyapp.repository.client.LlmMessage;

import tools.jackson.databind.json.JsonMapper;

/** Builds the LLM conversation: system prompt, portfolio context, history and the new message. */
@Component
public class ChatPromptBuilder {

	private final JsonMapper mapper;
	private final String systemPrompt;

	public ChatPromptBuilder(JsonMapper mapper) {
		this.mapper = mapper;
		this.systemPrompt = resource("chat/system-prompt.md");
	}

	/** Portfolio and watchlist, serialized as compact JSON for the model. */
	record Context(Portfolio portfolio, List<WatchlistItem> watchlist) {
	}

	public List<LlmMessage> build(Portfolio portfolio, List<WatchlistItem> watchlist,
			List<ChatMessage> history, String userMessage) {
		List<LlmMessage> messages = new ArrayList<>();
		messages.add(LlmMessage.system(systemPrompt));
		messages.add(LlmMessage.system("Portfolio context: " + mapper.writeValueAsString(new Context(portfolio, watchlist))));
		history.forEach(m -> messages.add(toLlm(m)));
		messages.add(LlmMessage.user(userMessage));
		return messages;
	}

	private LlmMessage toLlm(ChatMessage message) {
		if (!ChatMessage.ASSISTANT.equals(message.role())) {
			return LlmMessage.user(message.content());
		}
		String actions = message.actions() == null ? "" : "\nActions: " + mapper.writeValueAsString(message.actions());
		return LlmMessage.assistant(message.content() + actions);
	}

	static String resource(String path) {
		try (var in = new ClassPathResource(path).getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
