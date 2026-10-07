package com.campuspulse.service.embedding;

import com.campuspulse.repository.embedding.VectorSearchRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class VectorSearchService {

    private final VectorSearchRepository vectorSearchRepository;

    public VectorSearchService(
            VectorSearchRepository vectorSearchRepository) {

        this.vectorSearchRepository = vectorSearchRepository;
    }

    public List<Map<String, Object>> findSimilarTopics(
            Long topicId,
            int limit) {

        return vectorSearchRepository.findSimilarTopics(
                topicId,
                limit
        );
    }
}