# Course-based syllabi: program, semester and department sharing (Phase 6.3, backend)
- Type: feature (data model + access)
- Area: backend (database, API)
- Commit: see `git log --grep "Add course-based syllabi backend"`

## Why
Syllabi belong to courses (B.E. CSE, B.Tech IT, B.E. ECE, MCA…) and semesters, and must be
visible to the right people: faculty see their own department only (owner decision).

## What changed
- `V4__syllabus_visibility.sql`: `syllabus.visibility` (`PRIVATE` | `DEPARTMENT`, default
  PRIVATE); existing staff uploads → DEPARTMENT, student uploads stay PRIVATE; indexes.
- New `SyllabusAccess` (pure rules):
  - **Read**: owner · platform admin · college admin of its college · if DEPARTMENT: HOD and
    faculty of its department, students of its program.
  - **Change** (rename, details, curriculum edits, re-process, delete): owner · platform
    admin · college admin · HOD of its department.
  - Default visibility: staff uploads DEPARTMENT, student uploads PRIVATE.
- `SyllabusService`: `listReadable(user)` (rows add visibility, semester, owner, department,
  program, `mine`, `canManage`), `getSyllabusById` now = readable, `requireManageable`,
  `updateDetails` (name, programId, semester, visibility), program/semester validation
  (program must be in the syllabus' college and sets its department; semester within the
  program's semesters).
- `POST /api/syllabus/upload` takes optional `programId`, `semester`; students' uploads use their
  own program. `PUT /api/syllabus/{id}` accepts `{name, programId, semester, visibility}`.
- Reports, compare, recommendations, curriculum view and processing status work on any
  readable syllabus; processing, curriculum edits and delete need "change" rights.
- Tests: `SyllabusAccessTest` (sharing by department/program/college, private, who may change,
  default visibility); `CurriculumEditServiceTest` updated. All tests pass.

## How to test (restart backend → Flyway V4)
1. faculty@ uploads a syllabus with program B.E. CSE, semester 5 → hod@ and student@ (B.E. CSE)
   see it in Syllabi; a faculty of IT does not.
2. student@ opens it, runs a Report → works; Edit curriculum / Delete refused (403).
3. hod@ renames it or edits its curriculum → allowed.
4. student@ uploads own syllabus → private; faculty@ doesn't see it, hod@ does (department).

## Risk / rollback
Visibility widened for existing staff syllabi within their department (intended). To keep one
private: `PUT /api/syllabus/{id} {"visibility":"PRIVATE"}`. Rollback needs DB restore.
