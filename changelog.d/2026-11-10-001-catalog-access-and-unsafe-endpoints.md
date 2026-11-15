# Protect the shared job catalog and remove unsafe endpoints
- Type: security
- Area: backend, frontend
- Commit: see `git log --grep "Protect shared job catalog"`

## Why
Any logged-in user could change or bulk-import jobs in the shared catalog that every
user's analysis depends on. Several unused endpoints leaked data across users or let
anyone use the Groq key freely. Nobody could become ADMIN.

## What changed
- New rule `JobPostingService.requireModifiableJob`: a job can be changed only by its
  owner; shared catalog jobs (no owner) only by an ADMIN. Returns 403 otherwise.
  - `POST /api/jobs/{id}/skills` — now uses the rule (was: anyone with read access).
  - `POST /api/ai/jobs/{id}/extract-skills` — now uses the rule.
- `POST /api/job-catalog/import` — ADMIN only (`SecurityConfig`).
- New `config/AdminBootstrap.java` + property `app.admin-emails=${ADMIN_EMAILS:}`:
  listed emails get ADMIN at startup and on registration.
- Removed (no frontend caller):
  - `POST /api/syllabus` (raw entity binding could overwrite another user's syllabus)
  - `POST /api/embeddings/topics/{id}`, `POST /api/embeddings/syllabus/{id}`,
    `GET /api/embeddings/similar/{id}` (no ownership checks) +
    `TopicEmbeddingService`, `VectorSearchService`, `VectorSearchRepository.findSimilarTopics`
  - `GET /api/ai/test` (free-form LLM proxy)
- Frontend `JobMarket.jsx`: clear messages when import / re-extract is refused (403).
- Test: `JobPostingServiceAccessTest` (4 cases).

## How to test
1. Start backend with `$env:ADMIN_EMAILS = "you@example.com"` and restart.
2. As a normal user: Job Market → "Extract with AI" on a catalog job → message
   "Only an admin can…"; CSV import → "Only an admin can import…".
3. As the admin email: both actions work.
4. Paste a JD (private job) as a normal user → still works, and re-extract on it works.
5. `GET /api/ai/test` → 404 (was 200).

## Risk / rollback
Existing users keep role USER unless listed in ADMIN_EMAILS. Revert the commit to undo.
