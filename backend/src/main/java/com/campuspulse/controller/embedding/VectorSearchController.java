package com.campuspulse.controller.embedding;

import com.campuspulse.service.embedding.VectorSearchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/embeddings")
public class VectorSearchController {

    private final VectorSearchService vectorSearchService;

    public VectorSearchController(
            VectorSearchService vectorSearchService) {

        this.vectorSearchService = vectorSearchService;
    }

    @GetMapping("/similar/{topicId}")
    public List<Map<String, Object>> findSimilarTopics(
            @PathVariable Long topicId,
            @RequestParam(defaultValue = "5") int limit) {

        return vectorSearchService.findSimilarTopics(
                topicId,
                limit
        );
    }
}