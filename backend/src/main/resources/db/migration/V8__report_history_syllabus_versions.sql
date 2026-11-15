-- Phase 5.3: report history and syllabus versions (idempotent).

-- One row per computed gap report; kept when results are cleared, so coverage can be tracked over time.
CREATE TABLE IF NOT EXISTS report_snapshot (
    id             BIGSERIAL PRIMARY KEY,
    syllabus_id    BIGINT NOT NULL REFERENCES syllabus(id) ON DELETE CASCADE,
    job_id         BIGINT NOT NULL REFERENCES job_posting(id) ON DELETE CASCADE,
    coverage       DOUBLE PRECISION NOT NULL,
    total_skills   INT NOT NULL,
    matched_skills INT NOT NULL,
    partial_skills INT NOT NULL,
    gap_skills     INT NOT NULL,
    algo_version   INT NOT NULL,
    report_json    TEXT,
    created_at     TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_report_snapshot_pair ON report_snapshot (syllabus_id, job_id, created_at);

-- A revision points at the syllabus it replaces.
ALTER TABLE syllabus ADD COLUMN IF NOT EXISTS previous_version_id BIGINT REFERENCES syllabus(id) ON DELETE SET NULL;
ALTER TABLE syllabus ADD COLUMN IF NOT EXISTS version_no INT NOT NULL DEFAULT 1;
