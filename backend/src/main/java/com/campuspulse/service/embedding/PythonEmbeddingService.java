package com.campuspulse.service.embedding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class PythonEmbeddingService implements EmbeddingService {

    private final WebClient webClient;

    public PythonEmbeddingService(
            WebClient.Builder webClientBuilder,
            @Value("${embedding.service.url:http://127.0.0.1:8000}") String embeddingServiceUrl) {

        this.webClient = webClientBuilder
                .baseUrl(embeddingServiceUrl)
                .build();
    }

    @Override
    public List<Double> generateEmbedding(String text) {

        EmbeddingRequest request =
                new EmbeddingRequest(text);

        EmbeddingResponse response = webClient.post()
                .uri("/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(EmbeddingResponse.class)
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

    private record EmbeddingRequest(String text) {
    }

    private record EmbeddingResponse(
            String text,
            int dimension,
            List<Double> embedding) {
    }
}