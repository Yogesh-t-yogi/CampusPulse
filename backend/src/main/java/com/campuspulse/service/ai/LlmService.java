package com.campuspulse.service.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class LlmService {

    private final WebClient webClient;

    @Value("${llm.api.url}")
    private String apiUrl;

    @Value("${llm.api.key}")
    private String apiKey;

    @Value("${llm.model}")
    private String model;

    public LlmService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public String generateResponse(String prompt) {

        Map<String, Object> requestBody = Map.of(
        "model", model,
        "messages", new Object[]{
                Map.of(
                        "role", "user",
                        "content", prompt
                )
        },
        "temperature", 0.2,
        "max_tokens", 8000
        );

        Map response = webClient.post()
                .uri(apiUrl)
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + apiKey
                )
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) {
            throw new RuntimeException("Empty response received from LLM");
        }

        return extractContent(response);
    }

    private String extractContent(Map response) {

        Object choicesObject = response.get("choices");

        if (!(choicesObject instanceof java.util.List<?> choices)
                || choices.isEmpty()) {

            throw new RuntimeException(
                    "LLM response does not contain choices"
            );
        }

        Object firstChoice = choices.get(0);

        if (!(firstChoice instanceof Map<?, ?> choice)) {
            throw new RuntimeException(
                    "Invalid LLM response format"
            );
        }

        Object messageObject = choice.get("message");

        if (!(messageObject instanceof Map<?, ?> message)) {
            throw new RuntimeException(
                    "LLM response does not contain message"
            );
        }

        Object content = message.get("content");

        if (content == null) {
            throw new RuntimeException(
                    "LLM response does not contain content"
            );
        }

        return content.toString();
    }
}