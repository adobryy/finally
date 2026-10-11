package com.finallyapp.service;

/** A business rule violation whose message is safe to show to the user. */
public abstract class DomainException extends RuntimeException {

	protected DomainException(String message) {
		super(message);
	}
}
