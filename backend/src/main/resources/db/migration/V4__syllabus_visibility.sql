-- V4 (Phase 6.3): who besides the owner may read a syllabus.
-- PRIVATE = owner (and admins); DEPARTMENT = also its department's staff and its program's students.
ALTER TABLE syllabus ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE';

-- Existing staff uploads become department syllabi; student uploads stay private.
UPDATE syllabus s
SET visibility = 'DEPARTMENT'
FROM app_user u
WHERE u.id = s.user_id
  AND u.role IN ('SUPER_ADMIN', 'ADMIN', 'HOD', 'FACULTY');

CREATE INDEX idx_syllabus_department ON syllabus(department_id);
CREATE INDEX idx_syllabus_program ON syllabus(program_id);
