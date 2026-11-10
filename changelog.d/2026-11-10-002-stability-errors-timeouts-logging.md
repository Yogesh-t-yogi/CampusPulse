# Stability: re-processing fix, clear errors, timeouts, quieter logs
- Type: fix
- Area: backend
- Commit: see `git log --grep "Stabilize syllabus processing"`

## Why
- Re-processing a syllabus that already had topic vectors would hit a foreign-key error
  (`topic_embedding` → `topic`).
- AI / embedding failures returned a bare 500, so the UI could only say "failed".
- Calls to Groq and the AI service had no timeout and could hang a request forever.
- One failed embedding stopped indexing for all remaining topics, silently creating extra GAPs.
- Logs printed syllabus text and full security DEBUG output; error text mentioned HF_TOKEN.

## What changed
- `TopicEmbeddingJdbcRepository.deleteBySyllabusId` + called first in
  `SyllabusPersistenceService.saveAiStructure` (vectors deleted, then topics replaced).
- `SyllabusPersistenceService.generateEmbeddings`: continues past single failures; stops
  early only if the first 3 all fail (service down). Warning now says
  "N of M topics could not be indexed… Re-run processing from the Curriculum page".
- New `config/GlobalExceptionHandler.java`: every error is
  `{"status", "message", "error"}`. Mapping: bad input 400, too large 413,
  AI service unreachable / provider problem / timeout 503, DB error 500 (generic), other 500.
- `LlmService`: timeout `llm.timeout-seconds` (default 120, env `LLM_TIMEOUT_SECONDS`),
  1 retry on 429/5xx, readable messages for rate limit / bad key / timeout.
- `PythonEmbeddingService`: 30 s timeout.
- Logging: `System.out`/`printStackTrace` → SLF4J; syllabus text preview no longer logged.
  `show-sql` default false (`SHOW_SQL=true` to enable), security log level default INFO
  (`SECURITY_LOG_LEVEL=DEBUG` to enable).
- Removed stale Hugging Face / HF_TOKEN messages.

## How to test
1. Upload + process a syllabus, then on Curriculum click re-process → succeeds (before: DB error).
2. Stop the AI service (port 8000) and process a syllabus → curriculum is saved and the
   notice says topics could not be indexed and the AI service is not reachable.
3. Start backend with an invalid `GROQ_API_KEY` and paste a JD → message
   "The Groq API key was rejected…" (before: generic failure).
4. Backend console no longer prints SQL or syllabus text on upload.

## Risk / rollback
Error response bodies changed shape (added keys); frontend already reads `message`/`error`.
Revert the commit to undo.
