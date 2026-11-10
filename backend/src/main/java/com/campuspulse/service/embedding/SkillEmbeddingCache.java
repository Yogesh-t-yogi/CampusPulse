package com.campuspulse.service.embedding;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Returns pgvector literals for skills, computing and storing only the ones
 * not seen before. Key = lower-cased, trimmed skill text.
 */
@Service
public class SkillEmbeddingCache {

    private static final int BATCH_SIZE = 64;

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;

    public SkillEmbeddingCache(
            JdbcTemplate jdbcTemplate,
            EmbeddingService embeddingService) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingService = embeddingService;
    }

    public static String key(String skill) {
        return skill.trim().toLowerCase(Locale.ROOT);
    }

    /** Map of skill text (as given) → vector literal. */
    public Map<String, String> vectorsFor(List<String> skills) {
        Map<String, String> byKey = new HashMap<>();
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        skills.forEach(skill -> keys.add(key(skill)));

        for (String key : keys) {
            List<String> found = jdbcTemplate.queryForList(
                    "SELECT embedding::text FROM skill_embedding WHERE skill_key = ? AND model = ?",
                    String.class, key, EmbeddingService.MODEL);
            if (!found.isEmpty()) {
                byKey.put(key, found.get(0));
            }
        }

        List<String> missing = new ArrayList<>(keys);
        missing.removeAll(byKey.keySet());
        for (int start = 0; start < missing.size(); start += BATCH_SIZE) {
            List<String> batch = missing.subList(start, Math.min(start + BATCH_SIZE, missing.size()));
            List<List<Double>> vectors = embeddingService.generateEmbeddings(batch);
            for (int i = 0; i < batch.size(); i++) {
                String literal = EmbeddingService.toVectorLiteral(vectors.get(i));
                jdbcTemplate.update("""
                        INSERT INTO skill_embedding (skill_key, embedding, model)
                        VALUES (?, CAST(? AS vector), ?)
                        ON CONFLICT (skill_key) DO UPDATE
                            SET embedding = EXCLUDED.embedding, model = EXCLUDED.model
                        """, batch.get(i), literal, EmbeddingService.MODEL);
                byKey.put(batch.get(i), literal);
            }
        }

        Map<String, String> result = new HashMap<>();
        skills.forEach(skill -> result.put(skill, byKey.get(key(skill))));
        return result;
    }
}
