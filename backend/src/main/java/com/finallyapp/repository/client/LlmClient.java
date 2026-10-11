package com.finallyapp.repository.client;

import java.util.List;

import com.finallyapp.model.LlmResponse;

import reactor.core.publisher.Mono;

/** A chat model that answers with the structured {@link LlmResponse}. */
public interface LlmClient {

	/** Complete the conversation; fails with {@link LlmUnavailableException} on any call or parse error. */
	Mono<LlmResponse> complete(List<LlmMessage> messages);
}
