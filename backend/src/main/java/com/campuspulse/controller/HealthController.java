package com.campuspulse.controller;

import com.campuspulse.service.ai.LlmService;
import com.campuspulse.service.syllabus.LlamaParseClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

@RestController
public class HealthController {

    private final WebClient embeddingClient;
    private final LlmService llmService;
    private final LlamaParseClient llamaParse;

    public HealthController(
            WebClient.Builder webClientBuilder,
            @Value("${embedding.service.url:http://127.0.0.1:8000}") String embeddingServiceUrl,
            LlmService llmService,
            LlamaParseClient llamaParse) {
        this.llamaParse = llamaParse;
        this.embeddingClient = webClientBuilder.baseUrl(embeddingServiceUrl).build();
        this.llmService = llmService;
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "application", "CampusPulse"
        );
    }

    // Used by the sidebar status light (logged-in users only).
    // "llmConfigured" only checks that some AI provider has a key; it does not call it.
    /** AI providers in fallback order and whether each is set up (no keys). */
    @GetMapping("/api/health/ai-providers")
    public java.util.List<Map<String, Object>> aiProviders() {
        java.util.List<Map<String, Object>> rows = new java.util.ArrayList<>(llmService.status());
        rows.add(0, Map.of("name", "llamaparse", "configured", llamaParse.configured(),
                "model", "PDF parsing, tier " + llamaParse.tier(), "visionModel", "pdf pages"));
        return rows;
    }

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

        boolean llmConfigured = llmService.anyConfigured();
        return Map.of(
                "embeddingService", embeddingUp,
                "llmConfigured", llmConfigured,
                "ready", embeddingUp && llmConfigured
        );
    }
}
