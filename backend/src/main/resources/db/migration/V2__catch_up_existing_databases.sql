-- V2: brings databases created before Flyway (Hibernate ddl-auto + startup runners)
-- up to the V1 shape. Every statement is safe to run on a database that already has it.
-- Replaces the old startup classes UnitNameSchemaMigration and SchemaSetup.

CREATE EXTENSION IF NOT EXISTS vector;

-- Was UnitNameSchemaMigration: long unit titles.
ALTER TABLE unit ALTER COLUMN name TYPE TEXT;

-- Change 008: background processing status.
ALTER TABLE syllabus ADD COLUMN IF NOT EXISTS processing_status VARCHAR(30);
ALTER TABLE syllabus ADD COLUMN IF NOT EXISTS processing_message TEXT;

-- Change 006: skill vector cache.
CREATE TABLE IF NOT EXISTS skill_embedding (
    skill_key   TEXT PRIMARY KEY,
    embedding   vector(384) NOT NULL,
    model       VARCHAR(100) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

-- Change 007/019: saved analysis results.
CREATE TABLE IF NOT EXISTS analysis_result (
    id                    BIGSERIAL PRIMARY KEY,
    syllabus_id           BIGINT NOT NULL REFERENCES syllabus(id) ON DELETE CASCADE,
    job_id                BIGINT NOT NULL REFERENCES job_posting(id) ON DELETE CASCADE,
    report_json           TEXT,
    recommendations_json  TEXT,
    updated_at            TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (syllabus_id, job_id)
);
ALTER TABLE analysis_result ADD COLUMN IF NOT EXISTS algo_version INT NOT NULL DEFAULT 1;

-- Change 015: roles. Old "USER" accounts are faculty.
UPDATE app_user SET role = 'FACULTY' WHERE role = 'USER';
