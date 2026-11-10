# Curriculum editor (Phase 5.1)
- Type: feature
- Area: backend, frontend
- Commit: see `git log --grep "Add curriculum editor"`

## Why
AI extraction is sometimes wrong (merged topics, wrong unit names, missing items). The
only fix was re-processing the whole syllabus.

## What changed
Backend (owner of the syllabus only; other users get 404)
- New `CurriculumEditService` + `CurriculumEditController` under `/api/syllabus/{syllabusId}`:
  - `PUT /subjects/{id}` `{name, code}`
  - `POST /subjects/{id}/units` `{name, unitNumber?}` · `PUT /units/{id}` · `DELETE /units/{id}`
  - `POST /units/{id}/topics` `{name, description}` · `PUT /topics/{id}`
    `{name, description, unitId?}` (unitId = move to another unit of the same syllabus) ·
    `DELETE /topics/{id}`
- Topic vectors kept in sync: new / renamed topics are re-indexed (one embedding call);
  moved topics keep their vector; deleted topics/units delete their vectors first.
  If indexing fails the edit is kept and logged.
- Every change that affects matching clears saved reports for that syllabus.
- `TopicEmbeddingJdbcRepository`: `deleteByTopicId`, `deleteByUnitId`. Names max 255 chars.
- Test: `CurriculumEditServiceTest` (other user blocked, rename re-indexes + clears reports,
  move keeps topic, delete removes vector first, blank name rejected).
Frontend
- Curriculum page: **Edit curriculum** button. In edit mode: subject → Edit subject / Add unit;
  unit → Add topic / Rename unit / Delete unit; topic → edit (name, description, move to unit)
  / delete (with confirm). New `src/components/CurriculumEditors.jsx`.

## How to test
1. Curriculum → Edit curriculum.
2. Rename a topic → saved; open Report → recomputes (saved report was cleared).
3. Add a topic "Docker containers" to a unit → Report: Docker skill now MATCHED/PARTIAL.
4. Move a topic to another unit → appears there, not deleted.
5. Delete a unit → confirm → unit and its topics gone.
6. As another account, `PUT /api/syllabus/<someone else's id>/topics/<id>` → 404.

## Risk / rollback
Re-processing the syllabus with AI replaces hand edits (as before). Revert to undo.
