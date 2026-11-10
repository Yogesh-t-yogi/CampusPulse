-- V7: student profile (target roles, skills, chosen course), learning plan progress,
-- and cached per-skill learning roadmaps.

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS course_syllabus_id BIGINT
    REFERENCES syllabus(id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS student_target_role (
    user_id     BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    job_id      BIGINT NOT NULL REFERENCES job_posting(id) ON DELETE CASCADE,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, job_id)
);

-- source: RESUME (from resume match, with consent), SELF (typed), LEARNED (finished in the plan)
CREATE TABLE IF NOT EXISTS student_skill (
    user_id     BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    skill       VARCHAR(255) NOT NULL,
    skill_key   VARCHAR(255) NOT NULL,
    source      VARCHAR(20) NOT NULL DEFAULT 'SELF',
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, skill_key)
);

-- status: TODO, LEARNING, DONE
CREATE TABLE IF NOT EXISTS student_learning (
    user_id     BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    skill       VARCHAR(255) NOT NULL,
    skill_key   VARCHAR(255) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'TODO',
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, skill_key)
);

CREATE TABLE IF NOT EXISTS skill_roadmap (
    skill_key     VARCHAR(255) PRIMARY KEY,
    roadmap_json  TEXT NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);
