package com.campuspulse.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Creates helper tables that are managed with plain SQL (not JPA).
 * Every statement is idempotent, so it is safe on every startup.
 */
@Component
public class SchemaSetup implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public SchemaSetup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Cache of job-skill vectors, shared by all users (skills are not private).
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS skill_embedding (
                    skill_key  TEXT PRIMARY KEY,
                    embedding  vector(384) NOT NULL,
                    model      VARCHAR(100) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT now()
                )
                """);

        // Latest saved gap report + recommendations per syllabus/job pair.
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS analysis_result (
                    id                   BIGSERIAL PRIMARY KEY,
                    syllabus_id          BIGINT NOT NULL REFERENCES syllabus(id) ON DELETE CASCADE,
                    job_id               BIGINT NOT NULL REFERENCES job_posting(id) ON DELETE CASCADE,
                    report_json          TEXT,
                    recommendations_json TEXT,
                    updated_at           TIMESTAMP NOT NULL DEFAULT now(),
                    UNIQUE (syllabus_id, job_id)
                )
                """);
        // Which matching rules produced a saved result; older ones are recomputed.
        jdbcTemplate.execute(
                "ALTER TABLE analysis_result ADD COLUMN IF NOT EXISTS algo_version INT NOT NULL DEFAULT 1");
    }
}
