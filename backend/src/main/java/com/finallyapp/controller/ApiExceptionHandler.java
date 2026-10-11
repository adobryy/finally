package com.finallyapp.controller;

import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.result.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

import com.finallyapp.service.InvalidTickerException;
import com.finallyapp.service.TickerAlreadyWatchedException;
import com.finallyapp.service.TickerNotWatchedException;
import com.finallyapp.service.TradeRejectedException;

import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;

/** Maps domain and framework errors to RFC 9457 ProblemDetail responses. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler({InvalidTickerException.class, TradeRejectedException.class})
	ProblemDetail badRequest(RuntimeException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	@ExceptionHandler(TickerAlreadyWatchedException.class)
	ProblemDetail alreadyWatched(TickerAlreadyWatchedException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
	}

	@ExceptionHandler(TickerNotWatchedException.class)
	ProblemDetail notWatched(TickerNotWatchedException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}

	/** Lists every failing field, e.g. "quantity: must be greater than 0". */
	@Override
	protected Mono<ResponseEntity<Object>> handleWebExchangeBindException(WebExchangeBindException ex,
			HttpHeaders headers, HttpStatusCode status, ServerWebExchange exchange) {
		ex.getBody().setDetail(ex.getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.sorted()
				.collect(Collectors.joining("; ")));
		return super.handleWebExchangeBindException(ex, headers, status, exchange);
	}

	/** Names the unreadable field, reports malformed JSON, or falls back to the framework's reason. */
	@Override
	protected Mono<ResponseEntity<Object>> handleServerWebInputException(ServerWebInputException ex,
			HttpHeaders headers, HttpStatusCode status, ServerWebExchange exchange) {
		JacksonException jackson = jacksonCause(ex);
		if (jackson != null) {
			ex.getBody().setDetail(jackson.getPath().isEmpty()
					? "Malformed JSON request body"
					: "Invalid value for field '" + fieldPath(jackson) + "'");
		} else if (ex.getReason() != null) {
			ex.getBody().setDetail(ex.getReason());
		}
		return super.handleServerWebInputException(ex, headers, status, exchange);
	}

	private static JacksonException jacksonCause(Throwable e) {
		for (Throwable t = e; t != null; t = t.getCause()) {
			if (t instanceof JacksonException jackson) {
				return jackson;
			}
		}
		return null;
	}

	private static String fieldPath(JacksonException e) {
		return e.getPath().stream()
				.map(ref -> ref.getPropertyName() != null ? ref.getPropertyName() : "[" + ref.getIndex() + "]")
				.collect(Collectors.joining("."));
	}
}
