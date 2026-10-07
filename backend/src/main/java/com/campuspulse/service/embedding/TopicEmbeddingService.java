package com.campuspulse.service.embedding;

import com.campuspulse.entity.syllabus.Topic;
import com.campuspulse.repository.embedding.TopicEmbeddingJdbcRepository;
import com.campuspulse.repository.syllabus.TopicRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TopicEmbeddingService {

    private static final String MODEL =
            "sentence-transformers/all-MiniLM-L6-v2";

    private final TopicRepository topicRepository;
    private final EmbeddingService embeddingService;
    private final TopicEmbeddingJdbcRepository embeddingRepository;

    public TopicEmbeddingService(
            TopicRepository topicRepository,
            EmbeddingService embeddingService,
            TopicEmbeddingJdbcRepository embeddingRepository) {

        this.topicRepository = topicRepository;
        this.embeddingService = embeddingService;
        this.embeddingRepository = embeddingRepository;
    }

    public void generateEmbeddingForTopic(Long topicId) {

        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Topic not found: " + topicId));

        String text = topic.getName();

        if (text == null || text.isBlank()) {
            throw new RuntimeException(
                    "Topic name is empty for topic: " + topicId);
        }

        List<Double> vector =
                embeddingService.generateEmbedding(text);

        if (vector.size() != 384) {
            throw new RuntimeException(
                    "Expected 384 dimensions but received "
                            + vector.size());
        }

        String vectorString =
                vector.toString();

        embeddingRepository.saveEmbedding(
                topicId,
                vectorString,
                MODEL
        );
    }
}