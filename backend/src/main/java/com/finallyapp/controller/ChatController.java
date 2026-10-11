package com.finallyapp.controller;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finallyapp.controller.dto.ChatRequest;
import com.finallyapp.model.ChatMessage;
import com.finallyapp.model.ChatReply;
import com.finallyapp.service.ChatService;

import reactor.core.publisher.Mono;

/** AI assistant chat and its history. */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

	private final ChatService chatService;

	public ChatController(ChatService chatService) {
		this.chatService = chatService;
	}

	@PostMapping
	public Mono<ChatReply> chat(@Validated @RequestBody ChatRequest request) {
		return chatService.handle(request.message());
	}

	@GetMapping("/history")
	public Mono<List<ChatMessage>> history() {
		return chatService.history();
	}
}
