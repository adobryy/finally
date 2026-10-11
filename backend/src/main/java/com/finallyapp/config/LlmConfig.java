package com.finallyapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.reactive.function.client.WebClient;

import com.finallyapp.repository.client.LlmClient;
import com.finallyapp.repository.client.MockLlmClient;
import com.finallyapp.repository.client.OpenRouterLlmClient;

import tools.jackson.databind.json.JsonMapper;

/** Selects the LLM client: the deterministic mock when {@code LLM_MOCK=true}, otherwise OpenRouter. */
@Configuration
public class LlmConfig {

	@Bean
	LlmClient llmClient(AppProperties properties, WebClient.Builder webClient, JsonMapper mapper) throws Exception {
		if (properties.llmMock()) {
			return new MockLlmClient();
		}
		AppProperties.OpenRouter openRouter = properties.openrouter();
		try (var schema = new ClassPathResource("chat/response-schema.json").getInputStream()) {
			return new OpenRouterLlmClient(
					OpenRouterLlmClient.webClient(webClient, openRouter.baseUrl(), openRouter.apiKey()),
					openRouter.model(), mapper.readTree(schema), mapper);
		}
	}
}
