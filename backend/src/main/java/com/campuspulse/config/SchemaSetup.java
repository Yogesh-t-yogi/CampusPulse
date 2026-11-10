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
    }
}
