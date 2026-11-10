# Student profile, readiness, learning plan and market insights (backend)
- Type: feature
- Area: backend (database, AI, API)
- Commit: see `git log --grep "Add student features backend"`

## Why
Owner: students need a dashboard, profile, resume, target roles, and what/where/how to learn;
resume match and market insights need a rework. Plan: `_claude/plans/13-student-features.md`.

## What changed
- `V7__student_profile_learning.sql`: `student_target_role`, `student_skill` (source RESUME/
  SELF/LEARNED), `student_learning` (TODO/LEARNING/DONE), `skill_roadmap` (cached),
  `app_user.course_syllabus_id`. `SchemaVersionCheck.EXPECTED_VERSION` = 7.
- `StudentService` + `/api/student/*`:
  `GET profile`; `PUT target-roles {jobIds}` (≤5, must be visible); `POST skills {skills, source}`;
  `DELETE skills?skill=`; `PUT course {syllabusId}`;
  `GET dashboard` → per target role readiness % and per-skill status for the student
  (HAVE 1 · TAUGHT 0.7 · PARTLY 0.35 · TO_LEARN 0; curriculum from the student's course via saved
  gap reports), next 5 skills to learn, progress;
  `GET learning-plan` → TO_LEARN/PARTLY skills across target roles, sorted by #roles needing it then
  market demand, with status and resources; `PUT learning {skill, status}` (DONE adds the skill as
  LEARNED); `GET roadmap?skill=` → 4-step roadmap + project (LLM JSON, cached per skill);
  `GET resources?skill=`.
  Course = chosen syllabus, else newest syllabus shared with the student's program, else own newest.
- `LearningResources`: official docs for ~35 common skills + search links (freeCodeCamp, YouTube,
  NPTEL/SWAYAM, GeeksforGeeks, Coursera free) — no invented deep links.
- `InsightsService` + `/api/insights?location=&q=` → totalJobs, top 20 skills, top 10 roles,
  locations (visible jobs; filters). `/api/insights/gaps?syllabusId=&location=&q=` → top skills with
  status: students HAVE/TAUGHT/PARTLY/TO_LEARN (profile + course), staff TAUGHT/PARTLY/NOT_TAUGHT
  (given syllabus).
- `SemanticMatchingService.matchSkillNames(syllabusId, skills)`.
- Test: `LearningResourcesTest`.

## How to test
UI follows (042). Restart backend → Flyway V7. As student@: `PUT /api/student/target-roles
{"jobIds":[…]}`, `GET /api/student/dashboard`, `GET /api/student/roadmap?skill=Docker`.

## Risk / rollback
Additive schema. Revert to undo code.
