-- V4 (Phase 6.3): who besides the owner may read a syllabus.
-- PRIVATE = owner (and admins); DEPARTMENT = also its department's staff and its program's students.
-- Safe if Hibernate already added the column (without a default).

ALTER TABLE syllabus ADD COLUMN IF NOT EXISTS visibility VARCHAR(20);
ALTER TABLE syllabus ALTER COLUMN visibility SET DEFAULT 'PRIVATE';

-- Existing staff uploads become department syllabi; student uploads stay private.
UPDATE syllabus s
SET visibility = CASE
        WHEN u.role IN ('SUPER_ADMIN', 'ADMIN', 'HOD', 'FACULTY') THEN 'DEPARTMENT'
        ELSE 'PRIVATE'
    END
FROM app_user u
WHERE u.id = s.user_id
  AND s.visibility IS NULL;

UPDATE syllabus SET visibility = 'PRIVATE' WHERE visibility IS NULL;
ALTER TABLE syllabus ALTER COLUMN visibility SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_syllabus_department ON syllabus(department_id);
CREATE INDEX IF NOT EXISTS idx_syllabus_program ON syllabus(program_id);
