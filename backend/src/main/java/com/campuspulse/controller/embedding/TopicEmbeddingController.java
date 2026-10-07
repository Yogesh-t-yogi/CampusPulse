package com.campuspulse.controller.embedding;

import com.campuspulse.service.embedding.TopicEmbeddingService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/embeddings")
public class TopicEmbeddingController {

    private final TopicEmbeddingService topicEmbeddingService;

    public TopicEmbeddingController(
            TopicEmbeddingService topicEmbeddingService) {

        this.topicEmbeddingService = topicEmbeddingService;
    }

    // Generate embedding for one topic
    @PostMapping("/topics/{topicId}")
    public Map<String, Object> generateEmbedding(
            @PathVariable Long topicId) {

        topicEmbeddingService.generateEmbeddingForTopic(topicId);

        return Map.of(
                "topicId", topicId,
                "message", "Embedding generated successfully"
        );
    }

    // Generate embeddings for all topics in a syllabus
    @PostMapping("/syllabus/{syllabusId}")
    public Map<String, Object> generateEmbeddingsForSyllabus(
            @PathVariable Long syllabusId) {

        int count =
                topicEmbeddingService
                        .generateEmbeddingsForSyllabus(syllabusId);

        return Map.of(
                "syllabusId", syllabusId,
                "embeddingsGenerated", count,
                "message", "Embeddings generated successfully"
        );
    }
}