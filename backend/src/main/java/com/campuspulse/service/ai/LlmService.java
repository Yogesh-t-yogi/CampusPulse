package com.campuspulse.service.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LlmService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(LlmService.class);

    private final WebClient webClient;

    @Value("${llm.api.url}")
    private String apiUrl;

    @Value("${llm.api.key}")
    private String apiKey;

    @Value("${llm.model}")
    private String model;

    @Value("${llm.timeout-seconds:120}")
    private long timeoutSeconds;

    // Set to false after the provider rejects response_format once.
    private volatile boolean jsonModeSupported = true;

    public LlmService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /** Free text answer (temperature 0.2). */
    public String generateResponse(String prompt) {
        return generate(prompt, false);
    }

    /**
     * JSON answer: temperature 0 and the provider's JSON mode, so the reply is a
     * single valid JSON object. The prompt must ask for a JSON object.
     */
    public String generateJson(String prompt) {
        return generate(prompt, true);
    }

    private String generate(String prompt, boolean json) {

        String key = apiKey == null ? "" : apiKey.trim();

        if (key.isBlank()
                || key.contains("\r")
                || key.contains("\n")
                || key.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalStateException(
                    "GROQ_API_KEY is missing or contains invalid characters. "
                    + "Set a valid Groq API key and restart the backend."
            );
        }

        Map<String, Object> requestBody = new HashMap<>(Map.of(
                "model", model,
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", prompt
                        )
                ),
                "temperature", json ? 0.0 : 0.2,
                "max_tokens", 8000
        ));
        if (json && jsonModeSupported) {
            requestBody.put("response_format", Map.of("type", "json_object"));
        }

        Map<?, ?> response;
        try {
            try {
                response = send(key, requestBody);
            } catch (WebClientResponseException.BadRequest e) {
                if (!requestBody.containsKey("response_format")) {
                    throw e;
                }
                // Model doesn't support JSON mode: remember and retry without it.
                log.warn("LLM rejected JSON mode; continuing without it: {}", e.getMessage());
                jsonModeSupported = false;
                requestBody.remove("response_format");
                response = send(key, requestBody);
            }
        } catch (RuntimeException exception) {

            WebClientResponseException http =
                    findCause(exception, WebClientResponseException.class);
            if (http != null) {
                int status = http.getStatusCode().value();
                throw new IllegalStateException(status == 429
                        ? "The AI provider rate limit was reached. Wait a minute and try again."
                        : status == 401
                            ? "The Groq API key was rejected. Check GROQ_API_KEY."
                            : "The AI provider returned HTTP " + status + ".");
            }
            if (findCause(exception, TimeoutException.class) != null) {
                throw new IllegalStateException(
                        "The AI provider did not respond within "
                                + timeoutSeconds + " seconds. Try again.");
            }
            throw exception;
        }

        if (response == null) {
            throw new RuntimeException(
                    "Empty response received from Groq"
            );
        }

        return extractContent(response);
    }

    private Map<?, ?> send(String key, Map<String, Object> requestBody) {
        return webClient.post()
                    .uri(apiUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    // One retry for rate limits / temporary server errors.
                    .retryWhen(Retry.backoff(1, Duration.ofSeconds(3))
                            .filter(LlmService::isRetryable))
                    .block();
    }

    private static boolean isRetryable(Throwable error) {
        if (error instanceof WebClientResponseException response) {
            int status = response.getStatusCode().value();
            return status == 429 || status >= 500;
        }
        return false;
    }

    private static <T extends Throwable> T findCause(
            Throwable error, Class<T> type) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
        }
        return null;
    }

    private String extractContent(Map<?, ?> response) {

        Object choicesObject = response.get("choices");

        if (!(choicesObject instanceof List<?> choices)
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
