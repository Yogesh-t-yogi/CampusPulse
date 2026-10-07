package com.campuspulse.repository.embedding;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TopicEmbeddingJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public TopicEmbeddingJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveEmbedding(
            Long topicId,
            String embedding,
            String model) {

        String sql = """
                INSERT INTO topic_embedding
                    (topic_id, embedding, model)
                VALUES
                    (?, CAST(? AS vector), ?)
                ON CONFLICT (topic_id)
                DO UPDATE SET
                    embedding = EXCLUDED.embedding,
                    model = EXCLUDED.model
                """;

        jdbcTemplate.update(
                sql,
                topicId,
                embedding,
                model
        );
    }
}