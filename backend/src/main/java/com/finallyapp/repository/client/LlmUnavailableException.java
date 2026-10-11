package com.finallyapp.repository.client;

/** The LLM call failed, timed out or returned unparseable output. */
public class LlmUnavailableException extends RuntimeException {

	public LlmUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
