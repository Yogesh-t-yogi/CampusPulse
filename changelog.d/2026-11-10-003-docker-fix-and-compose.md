# Docker: fix image builds and add one-command local stack
- Type: infra
- Area: infra, repo
- Commit: see `git log --grep "Fix Docker builds"`

## Why
Review result: the three Dockerfiles are sound, so Docker is kept (not removed).
Defect found: no `.dockerignore` files, so `COPY . .` sent local folders into the images —
the frontend image would overwrite Linux `node_modules` with Windows ones (build breaks),
the backend copied `target/`, the AI service copied the Windows `venv/`.
There was also no way to start the whole stack locally with Docker.

## What changed
- New `frontend/.dockerignore`, `backend/.dockerignore`, `ai-service/.dockerignore`.
- `backend/mvnw` marked executable in git (needed by CI on Linux).
- New `docker-compose.yml`: `db` (pgvector/pgvector:pg17 + healthcheck),
  `ai-service` :8000, `backend` :8081 (waits for healthy DB), `frontend` :5173 (nginx).
- New `docker/init-db.sql` (enables the `vector` extension on first start).
- New `.env.example` (DB_PASSWORD, JWT_SECRET, GROQ_API_KEY, ADMIN_EMAILS).
- README: "Run Everything with Docker" and "Admin Access" sections.
- Dockerfiles themselves unchanged.

## How to test
Not run here (Docker is not installed on this machine). With Docker Desktop:
1. `Copy-Item .env.example .env`, fill in values.
2. `docker compose up --build` → open http://localhost:5173, register, upload a syllabus.
3. `docker compose down` keeps data; `down -v` wipes it.
Non-Docker local setup (README steps 1–6) is unaffected.

## Risk / rollback
Additive only. Delete the new files / revert the commit to undo.
