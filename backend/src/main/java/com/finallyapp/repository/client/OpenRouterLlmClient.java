package com.finallyapp.repository.client;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

import com.finallyapp.model.LlmResponse;

import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import tools.jackson.databind.json.JsonMapper;

/** OpenRouter chat completions, routed to Cerebras, with strict JSON-schema output. */
public class OpenRouterLlmClient implements LlmClient {

	static final Duration TIMEOUT = Duration.ofSeconds(30);
	/** Without a cap OpenRouter reserves the model's full context; low-credit accounts then skip Cerebras. */
	static final int MAX_TOKENS = 4096;
	static final String SCHEMA_NAME = "finally_response";

	private static final Logger log = LoggerFactory.getLogger(OpenRouterLlmClient.class);

	private final WebClient webClient;
	private final String model;
	private final JsonNode schema;
	private final JsonMapper mapper;

	public OpenRouterLlmClient(WebClient webClient, String model, JsonNode schema, JsonMapper mapper) {
		this.webClient = webClient;
		this.model = model;
		this.schema = schema;
		this.mapper = mapper;
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	record Request(String model, Provider provider, Reasoning reasoning, int maxTokens, List<LlmMessage> messages,
			ResponseFormat responseFormat) {
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	record Provider(List<String> order, boolean requireParameters) {
	}

	record Reasoning(String effort) {
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	record ResponseFormat(String type, JsonSchema jsonSchema) {
	}

	record JsonSchema(String name, boolean strict, JsonNode schema) {
	}

	record Response(String provider, List<Choice> choices) {
	}

	record Choice(Message message) {
	}

	record Message(String content) {
	}

	@Override
	public Mono<LlmResponse> complete(List<LlmMessage> messages) {
		Request request = new Request(model,
				new Provider(List.of("cerebras"), true),
				new Reasoning("low"),
				MAX_TOKENS,
				messages,
				new ResponseFormat("json_schema", new JsonSchema(SCHEMA_NAME, true, schema)));
		return webClient.post()
				.uri("/chat/completions")
				.bodyValue(request)
				.retrieve()
				.bodyToMono(Response.class)
				.timeout(TIMEOUT)
				.map(this::parse)
				.onErrorMap(e -> !(e instanceof LlmUnavailableException),
						e -> new LlmUnavailableException("LLM request failed: " + e.getMessage(), e));
	}

	private LlmResponse parse(Response response) {
		if (response.choices() == null || response.choices().isEmpty()
				|| response.choices().getFirst().message() == null) {
			throw new LlmUnavailableException("LLM returned no message", null);
		}
		log.debug("LLM response served by {}", response.provider());
		String content = response.choices().getFirst().message().content();
		try {
			return mapper.readValue(content, LlmResponse.class);
		} catch (RuntimeException e) {
			throw new LlmUnavailableException("LLM returned unparseable output", e);
		}
	}

	/** A WebClient for the OpenRouter API with bearer authentication. */
	public static WebClient webClient(WebClient.Builder builder, String baseUrl, String apiKey) {
		return builder.baseUrl(baseUrl)
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
				.build();
	}
}
