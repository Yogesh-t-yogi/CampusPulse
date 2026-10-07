package com.campuspulse.repository.embedding;

import com.campuspulse.entity.embedding.TopicEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TopicEmbeddingRepository
        extends JpaRepository<TopicEmbedding, Long> {

    Optional<TopicEmbedding> findByTopicId(Long topicId);
}