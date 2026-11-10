package com.campuspulse.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

@RestController
public class HealthController {

    private final WebClient embeddingClient;
    private final String llmApiKey;

    public HealthController(
            WebClient.Builder webClientBuilder,
            @Value("${embedding.service.url:http://127.0.0.1:8000}") String embeddingServiceUrl,
            @Value("${llm.api.key:}") String llmApiKey) {
        this.embeddingClient = webClientBuilder.baseUrl(embeddingServiceUrl).build();
        this.llmApiKey = llmApiKey;
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "application", "CampusPulse"
        );
    }

    // Used by the sidebar status light (logged-in users only).
    // "llmConfigured" only checks that a key is set; it does not call Groq.
    @GetMapping("/api/health/services")
    public Map<String, Object> services() {
        boolean embeddingUp;
        try {
            embeddingClient.get().uri("/health")
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(3))
                    .block();
            embeddingUp = true;
        } catch (RuntimeException e) {
            embeddingUp = false;
        }

        boolean llmConfigured = llmApiKey != null && !llmApiKey.isBlank();
        return Map.of(
                "embeddingService", embeddingUp,
                "llmConfigured", llmConfigured,
                "ready", embeddingUp && llmConfigured
        );
    }
}
