# Process syllabi in the background with a status the UI polls
- Type: feature (reliability)
- Area: backend, frontend
- Commit: see `git log --grep "Process syllabi in the background"`

## Why
`POST /api/ai/syllabus/{id}/save` did AI structuring + topic indexing inside one HTTP
request (often minutes). Any proxy/browser timeout lost the result, there was no
progress, and the same syllabus could be processed twice at once.

## What changed
Backend
- `Syllabus`: new columns `processing_status` (PROCESSING, READY, READY_WITH_WARNINGS,
  FAILED; null = not processed) and `processing_message` (added by Hibernate on startup).
- New `service/ai/SyllabusProcessingService.java` (`@Async`, app has `@EnableAsync`):
  structure → save → "Indexing topics…" → embeddings → clear saved reports → READY /
  READY_WITH_WARNINGS / FAILED with a readable message. On startup, runs left in
  PROCESSING by a restart are marked FAILED ("run it again").
- `POST /api/ai/syllabus/{id}/save` now returns **202** `{syllabusId, status, message}` at
  once; **409** if already processing; 400 if no text.
- New `GET /api/ai/syllabus/{id}/status` → `{syllabusId, status, message, topicCount}`.
  Old syllabi without a status report READY if they have topics, else NOT_PROCESSED.
- `SyllabusPersistenceService.saveAiStructure(Long, …)` and `generateEmbeddings(Long)`
  now load data by id (safe outside a web request).
- Removed unused `GET /api/ai/syllabus/{id}/structure` (an LLM call no page used).
- `GET /api/syllabus` list now also includes `processingStatus` / `processingMessage`.

Frontend (behaviour only, no visual change)
- New `src/syllabusProcessing.js`: `processSyllabus`, `waitForProcessing` (polls every 3 s,
  max 15 min), `getProcessingStatus`.
- `SyllabusUpload.jsx`: starts processing and shows each stage message while waiting.
- `Curriculum.jsx`: re-process uses the same helper; opening Curriculum while processing
  is still running waits and reloads when done; a FAILED run shows its message.

## How to test
1. Upload a syllabus: the message changes "Extracting subjects…" → "Indexing topics…" →
   success; then Curriculum shows the tree.
2. Upload, then immediately open Curriculum: it shows "Extracting curriculum…" and fills
   in by itself when done.
3. Click process twice fast (or call the API twice) → second call gets 409.
4. Stop the backend during processing, start it again → status FAILED with
   "interrupted by a server restart"; processing again works.
5. Bad `GROQ_API_KEY` → upload ends with the "key was rejected" message.

## Risk / rollback
API contract of `/save` changed (202 + polling); old frontend builds would show success too
early. Revert the commit (backend + frontend together) to undo; the two new columns are
harmless if left.
