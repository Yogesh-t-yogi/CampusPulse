
package com.campuspulse.service.embedding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import reactor.core.publisher.Mono;

import java.time.Duration;

import java.util.List;

@Service
public class PythonEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(PythonEmbeddingService.class);

    private final WebClient webClient;

    public PythonEmbeddingService(
            WebClient.Builder webClientBuilder,
            @Value("${embedding.service.url:http://127.0.0.1:8000}")
            String embeddingServiceUrl) {

        this.webClient = webClientBuilder
                .baseUrl(embeddingServiceUrl)
                .build();
    }

    @Override
    public List<Double> generateEmbedding(String text) {

        EmbeddingRequest request = new EmbeddingRequest(text);

        EmbeddingResponse response = webClient.post()
                .uri("/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(EmbeddingResponse.class)
                .timeout(Duration.ofSeconds(30), Mono.error(
                        new IllegalStateException(
                                "The AI embedding service did not respond within 30 seconds.")))
                .block();

        if (response == null) {
            throw new RuntimeException(
                    "Embedding service returned an empty response");
        }

        if (response.dimension() != 384) {
            throw new RuntimeException(
                    "Expected 384-dimensional embedding but received "
                            + response.dimension());
        }

        return response.embedding();
    }

    @Override
    public List<List<Double>> generateEmbeddings(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }

        BatchEmbeddingResponse response;
        try {
            response = requestBatch(texts);
        } catch (WebClientResponseException.NotFound e) {
            // Older AI service without /embed/batch: embed one text at a time.
            log.warn("AI service has no /embed/batch; falling back to /embed. Restart the AI service to update it.");
            List<List<Double>> vectors = new ArrayList<>();
            for (String text : texts) {
                vectors.add(generateEmbedding(text));
            }
            return vectors;
        }

        if (response == null
                || response.embeddings() == null
                || response.embeddings().size() != texts.size()
                || response.embeddings().stream().anyMatch(e -> e == null || e.size() != 384)) {
            throw new IllegalStateException(
                    "Embedding service returned an invalid batch response.");
        }

        return response.embeddings();
    }

    private BatchEmbeddingResponse requestBatch(List<String> texts) {
        return webClient.post()
                .uri("/embed/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new BatchEmbeddingRequest(texts))
                .retrieve()
                .bodyToMono(BatchEmbeddingResponse.class)
                .timeout(Duration.ofSeconds(120), Mono.error(
                        new IllegalStateException(
                                "The AI embedding service did not respond within 120 seconds.")))
                .block();
    }

    private record EmbeddingRequest(String text) {
    }

    private record BatchEmbeddingRequest(List<String> texts) {
    }

    private record BatchEmbeddingResponse(
            int dimension,
            List<List<Double>> embeddings) {
    }

    private record EmbeddingResponse(
            String text,
            int dimension,
            List<Double> embedding) {
    }
}
