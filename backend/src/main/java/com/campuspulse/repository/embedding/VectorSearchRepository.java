package com.campuspulse.repository.embedding;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class VectorSearchRepository {

    private final JdbcTemplate jdbcTemplate;

    public VectorSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Search syllabus topics using an external embedding
    public List<Map<String, Object>> findSimilarTopicsByEmbedding(
            String embedding,
            Long syllabusId,
            int limit) {

        String sql = """
                SELECT
                    t.id,
                    t.name,
                    1 - (te.embedding <=> CAST(? AS vector))
                        AS similarity
                FROM topic_embedding te
                JOIN topic t
                    ON t.id = te.topic_id
                JOIN unit u
                    ON u.id = t.unit_id
                JOIN subject s
                    ON s.id = u.subject_id
                WHERE s.syllabus_id = ?
                ORDER BY te.embedding <=> CAST(? AS vector)
                LIMIT ?
                """;

        return jdbcTemplate.queryForList(
                sql,
                embedding,
                syllabusId,
                embedding,
                limit
        );
    }
}