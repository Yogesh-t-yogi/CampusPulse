# Save gap reports and AI recommendations instead of recomputing
- Type: feature (performance, consistency)
- Area: backend
- Commit: see `git log --grep "Save analysis results"`

## Why
Gap Analysis, AI Recommendations and Dashboard each recomputed the full report, and
Dashboard + Recommendations each made a new LLM call. Slow, used Groq tokens on every
page view, and recommendation text differed between pages.

## What changed
- New table `analysis_result(syllabus_id, job_id, report_json, recommendations_json,
  updated_at)`, unique per pair, rows deleted automatically if the syllabus or job is
  deleted (created by `SchemaSetup`).
- New `service/analysis/AnalysisResultService.java`:
  - `getReport(job, syllabus, refresh)` — saved report, or compute + save.
    A new report clears saved recommendations (they depend on it).
  - `getRecommendations(job, syllabus, refresh)` — saved, or one LLM call + save.
  - `clearForSyllabus`, `clearForJob`.
- Endpoints (same URLs, new optional `?refresh=true` to force a new run):
  - `GET /api/matching/semantic/report/jobs/{jobId}/syllabus/{syllabusId}`
  - `GET /api/recommendations/jobs/{jobId}/syllabus/{syllabusId}`
- Saved results are cleared when: a syllabus is (re-)processed
  (`SyllabusAiController`), a job's skills are re-extracted (`JobSkillAiController`) or a
  skill is added (`JobSkillController`).
- `SemanticGapReport` / `SemanticSkillMatch`: `@JsonCreator` constructors so saved JSON
  can be read back. Test: `SavedResultJsonTest`.
- Frontend unchanged (a visible "Re-run analysis" button comes with the Phase 3 UI work).

## How to test
1. Open Gap Analysis → first load computes; switch to Dashboard → loads instantly with
   the same numbers.
2. Open AI Recommendations, then Dashboard → the same recommendation text on both
   (before: different text each time). Backend log shows only one Groq call.
3. Re-process the syllabus in Curriculum → Gap Analysis computes fresh again.
4. Force a fresh run: call the report URL with `?refresh=true`.

## Risk / rollback
If a saved result looks wrong, use `?refresh=true` or `DELETE FROM analysis_result;`.
Revert the commit to undo (the table can stay; it is unused then).
