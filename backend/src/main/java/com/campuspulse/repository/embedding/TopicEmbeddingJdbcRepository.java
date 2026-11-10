package com.campuspulse.repository.embedding;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TopicEmbeddingJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public TopicEmbeddingJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Remove vectors for every topic of a syllabus (needed before its
    // topics are replaced, because topic_embedding references topic).
    public int deleteBySyllabusId(Long syllabusId) {
        return jdbcTemplate.update("""
                DELETE FROM topic_embedding te
                USING topic t, unit u, subject s
                WHERE te.topic_id = t.id
                    AND t.unit_id = u.id
                    AND u.subject_id = s.id
                    AND s.syllabus_id = ?
                """, syllabusId);
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