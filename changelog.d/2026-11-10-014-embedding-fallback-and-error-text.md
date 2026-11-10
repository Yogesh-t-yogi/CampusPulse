# Fall back to /embed when the AI service has no /embed/batch; show real report errors
- Type: fix
- Area: backend, frontend
- Commit: see `git log --grep "Fall back to single embeddings"`

## Why
Owner report: "34 of 34 topics could not be indexed … HTTP 404" and Report showing
"Unable to load analytics". Cause: backend was updated but the AI service was still the
old process without `POST /embed/batch` (confirmed: `/embed/batch` → 404, OpenAPI lists
only `/embed`, `/health`). Every batch call failed, so no topic vectors were saved and
skill matching failed too. The page only showed a generic message.

## What changed
- `PythonEmbeddingService.generateEmbeddings`: on 404 from `/embed/batch`, logs a warning
  ("Restart the AI service to update it") and embeds texts one by one via `/embed`.
  Old AI service = slower, but works.
- Report tabs (`Dashboard.jsx`, `GapAnalysis.jsx`, `AIRecommendations.jsx`): server errors
  now show the backend's message instead of a fixed generic sentence.

## How to test
1. Restart the AI service (`python -m uvicorn app:app --host 127.0.0.1 --port 8000`), then
   `curl -X POST localhost:8000/embed/batch -H "Content-Type: application/json" -d "{\"texts\":[\"java\"]}"` → 200.
2. Restart the backend. Curriculum → re-process the syllabus → "Curriculum ready: 34 topics indexed".
3. Report loads.
4. Optional: run the old AI service → processing still works (slower), backend log shows the fallback warning.

## Risk / rollback
None beyond extra calls in fallback mode. Revert the commit to undo.
