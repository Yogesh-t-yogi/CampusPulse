# One command to start AI service, backend and frontend
- Type: infra (developer experience)
- Area: repo
- Commit: see `git log --grep "Add one-command start"`

## Why
Owner ran three terminals and three commands every time.

## What changed
- New `start-dev.ps1` + `start-dev.cmd` (double-click, bypasses execution policy):
  1. loads settings from `.env` (git-ignored); stops with a clear message if `.env` or
     `DB_PASSWORD` is missing; makes a temporary JWT secret (with a warning) if `JWT_SECRET`
     is missing/short; warns when `GROQ_API_KEY` is empty or demo accounts are on;
  2. first run only: creates `ai-service\venv` + installs requirements, runs `npm install`;
  3. opens three titled windows: "CampusPulse - AI service" (uvicorn :8000),
     "CampusPulse - Backend" (`mvnw spring-boot:run` — always uses pom.xml, so no more stale
     IDE classpath), "CampusPulse - Frontend" (`npm run dev`);
  4. waits up to 4 minutes for `/api/health`, then opens http://localhost:5173.
- `.env.example` rewritten with comments for every setting.
- README: "Quick start (one command, Windows)".

## How to test
1. `Copy-Item .env.example .env`, fill DB_PASSWORD, GROQ_API_KEY, JWT_SECRET (32+ chars),
   DEMO_USERS_PASSWORD=<your demo password> for demo accounts.
2. Stop servers running elsewhere (VS Code backend, old terminals) — ports must be free.
3. Double-click `start-dev.cmd` → 3 windows → browser opens when ready.

## Risk / rollback
Additive. Delete the two files to undo.
