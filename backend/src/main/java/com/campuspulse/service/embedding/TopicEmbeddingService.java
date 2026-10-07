package com.campuspulse.service.embedding;

import com.campuspulse.entity.syllabus.Topic;
import com.campuspulse.repository.embedding.TopicEmbeddingJdbcRepository;
import com.campuspulse.repository.syllabus.TopicRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TopicEmbeddingService {

    private final TopicRepository topicRepository;
    private final EmbeddingService embeddingService;
    private final TopicEmbeddingJdbcRepository topicEmbeddingJdbcRepository;

    public TopicEmbeddingService(
            TopicRepository topicRepository,
            EmbeddingService embeddingService,
            TopicEmbeddingJdbcRepository topicEmbeddingJdbcRepository) {

        this.topicRepository = topicRepository;
        this.embeddingService = embeddingService;
        this.topicEmbeddingJdbcRepository =
                topicEmbeddingJdbcRepository;
    }

    // Generate embedding for one topic
    public void generateEmbeddingForTopic(Long topicId) {

        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Topic not found with id: " + topicId));

        String text = buildEmbeddingText(topic);

        List<Double> embedding =
                embeddingService.generateEmbedding(text);

        String vector = convertToVectorString(embedding);

        topicEmbeddingJdbcRepository.saveEmbedding(
                topic.getId(),
                vector,
                "sentence-transformers/all-MiniLM-L6-v2"
        );
    }

    // Generate embeddings for all topics in a syllabus
    public int generateEmbeddingsForSyllabus(Long syllabusId) {

        List<Topic> topics =
                topicRepository.findBySyllabusId(syllabusId);

        if (topics.isEmpty()) {
            throw new RuntimeException(
                    "No topics found for syllabus id: " + syllabusId);
        }

        int count = 0;

        for (Topic topic : topics) {

            String text = buildEmbeddingText(topic);

            List<Double> embedding =
                    embeddingService.generateEmbedding(text);

            String vector = convertToVectorString(embedding);

            topicEmbeddingJdbcRepository.saveEmbedding(
                    topic.getId(),
                    vector,
                    "sentence-transformers/all-MiniLM-L6-v2"
            );

            count++;
        }

        return count;
    }

    // Build text that will be converted into an embedding
    private String buildEmbeddingText(Topic topic) {

        String text = topic.getName();

        if (topic.getDescription() != null &&
                !topic.getDescription().isBlank()) {

            text += ". " + topic.getDescription();
        }

        return text;
    }

    // Convert List<Double> into PostgreSQL vector format
    private String convertToVectorString(List<Double> embedding) {

        return embedding.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
    }
}