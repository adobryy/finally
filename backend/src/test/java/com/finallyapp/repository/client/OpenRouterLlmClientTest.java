package com.finallyapp.repository.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.finallyapp.model.LlmResponse;
import com.finallyapp.model.LlmTrade;
import com.finallyapp.model.LlmWatchlistChange;
import com.finallyapp.model.Side;
import com.finallyapp.model.WatchlistAction;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import reactor.test.StepVerifier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class OpenRouterLlmClientTest {

	private final JsonMapper mapper = JsonMapper.builder()
			.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
			.build();
	private final MockWebServer server = new MockWebServer();
	private OpenRouterLlmClient client;

	@BeforeEach
	void setUp() throws IOException {
		server.start();
		JsonNode schema = mapper.readTree("{\"type\":\"object\",\"additionalProperties\":false}");
		WebClient webClient = OpenRouterLlmClient.webClient(WebClient.builder(), server.url("/api/v1").toString(), "test-key");
		client = new OpenRouterLlmClient(webClient, "openai/gpt-oss-120b", schema, mapper);
	}

	@AfterEach
	void tearDown() {
		server.close();
	}

	@Test
	void sendsOpenRouterRequestShape() throws Exception {
		server.enqueue(completion("{\"message\":\"Hi\",\"trades\":[],\"watchlist_changes\":[]}"));

		client.complete(List.of(LlmMessage.system("prompt"), LlmMessage.user("hello"))).block();

		RecordedRequest request = server.takeRequest();
		assertThat(request.getUrl().encodedPath()).isEqualTo("/api/v1/chat/completions");
		assertThat(request.getHeaders().get("Authorization")).isEqualTo("Bearer test-key");
		JsonNode body = mapper.readTree(request.getBody().utf8());
		assertThat(body.get("model").asString()).isEqualTo("openai/gpt-oss-120b");
		assertThat(body.at("/provider/order/0").asString()).isEqualTo("cerebras");
		assertThat(body.at("/provider/require_parameters").asBoolean()).isTrue();
		assertThat(body.at("/reasoning/effort").asString()).isEqualTo("low");
		assertThat(body.get("max_tokens").asInt()).isEqualTo(4096);
		assertThat(body.at("/messages/1/role").asString()).isEqualTo("user");
		assertThat(body.at("/messages/1/content").asString()).isEqualTo("hello");
		assertThat(body.at("/response_format/type").asString()).isEqualTo("json_schema");
		assertThat(body.at("/response_format/json_schema/name").asString()).isEqualTo("finally_response");
		assertThat(body.at("/response_format/json_schema/strict").asBoolean()).isTrue();
		assertThat(body.at("/response_format/json_schema/schema/additionalProperties").asBoolean()).isFalse();
	}

	@Test
	void parsesResponseWithActions() {
		server.enqueue(completion("""
				{"message":"Buying 10 AAPL.","trades":[{"ticker":"AAPL","side":"buy","quantity":10}],
				 "watchlist_changes":[{"ticker":"PYPL","action":"add"}]}"""));

		LlmResponse response = client.complete(List.of(LlmMessage.user("buy"))).block();

		assertThat(response).isEqualTo(new LlmResponse("Buying 10 AAPL.",
				List.of(new LlmTrade("AAPL", Side.BUY, 10)),
				List.of(new LlmWatchlistChange("PYPL", WatchlistAction.ADD))));
	}

	@Test
	void parsesResponseWithoutActions() {
		server.enqueue(completion("{\"message\":\"Your portfolio is balanced.\",\"trades\":[],\"watchlist_changes\":[]}"));

		LlmResponse response = client.complete(List.of(LlmMessage.user("how am I doing?"))).block();

		assertThat(response.message()).isEqualTo("Your portfolio is balanced.");
		assertThat(response.trades()).isEmpty();
		assertThat(response.watchlistChanges()).isEmpty();
	}

	@Test
	void malformedContentIsUnavailable() {
		server.enqueue(completion("Sure! I will buy AAPL."));

		StepVerifier.create(client.complete(List.of(LlmMessage.user("buy"))))
				.expectErrorSatisfies(e -> assertThat(e).isInstanceOf(LlmUnavailableException.class)
						.hasMessage("LLM returned unparseable output"))
				.verify();
	}

	@Test
	void invalidSideIsUnavailable() {
		server.enqueue(completion("{\"message\":\"x\",\"trades\":[{\"ticker\":\"AAPL\",\"side\":\"hold\",\"quantity\":1}],\"watchlist_changes\":[]}"));

		StepVerifier.create(client.complete(List.of(LlmMessage.user("x"))))
				.expectError(LlmUnavailableException.class)
				.verify();
	}

	@Test
	void missingChoicesIsUnavailable() {
		server.enqueue(json("{\"choices\":[]}"));

		StepVerifier.create(client.complete(List.of(LlmMessage.user("x"))))
				.expectErrorSatisfies(e -> assertThat(e).hasMessage("LLM returned no message"))
				.verify();
	}

	@Test
	void httpErrorIsUnavailable() {
		server.enqueue(new MockResponse.Builder().code(401).body("{\"error\":\"bad key\"}").build());

		StepVerifier.create(client.complete(List.of(LlmMessage.user("x"))))
				.expectErrorSatisfies(e -> assertThat(e).isInstanceOf(LlmUnavailableException.class)
						.hasMessageStartingWith("LLM request failed: 401"))
				.verify();
	}

	private MockResponse completion(String content) {
		return json(mapper.writeValueAsString(
				new OpenRouterLlmClient.Response("Cerebras", List.of(new OpenRouterLlmClient.Choice(
						new OpenRouterLlmClient.Message(content))))));
	}

	private static MockResponse json(String body) {
		return new MockResponse.Builder().addHeader("Content-Type", "application/json").body(body).build();
	}
}
