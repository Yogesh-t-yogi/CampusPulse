# Batch embeddings: one call per 64 topics instead of one per topic
- Type: feature (performance)
- Area: ai-service, backend
- Commit: see `git log --grep "Embed syllabus topics in batches"`

## Why
Processing a syllabus made one HTTP call to the AI service per topic (100 topics = 100
sequential round trips). FastEmbed can embed many texts at once.

## What changed
- `ai-service/app.py` (v1.3.0): new `POST /embed/batch` `{texts: [...]}` (1–128 texts) →
  `{dimension: 384, embeddings: [[...], ...]}`. Shared `normalize()` used by `/embed` and
  `/embed/batch`. `/embed` unchanged.
- `EmbeddingService`: new `generateEmbeddings(List<String>)`, shared `MODEL` constant and
  `toVectorLiteral()` helper.
- `PythonEmbeddingService`: batch client (120 s timeout, validates count and size).
- `SyllabusPersistenceService.generateEmbeddings`: batches of 64; a failed batch counts its
  topics as failed and continues; if the very first batch fails it stops (service down).

## How to test
- Verified locally: `/embed/batch` returns vectors identical (<1e-5) to `/embed`;
  empty list → 422, blank text → 400.
- Restart the AI service (new endpoint!), then process a syllabus: should be noticeably
  faster; backend console shows a few `/embed/batch` calls instead of one per topic.
- Gap report results for an existing syllabus are unchanged.

## Risk / rollback
The backend now needs the new AI service version — restart both together.
Revert the commit to undo.
