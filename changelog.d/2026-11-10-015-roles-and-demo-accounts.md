# Admin / Faculty / Student roles and local demo accounts
- Type: feature
- Area: backend, frontend, infra
- Commit: see `git log --grep "Add admin, faculty and student roles"`

## Why
Owner asked for separate logins for admin, faculty and student, with ready-made
accounts for local testing.

## What changed
Backend
- Roles are now `ADMIN`, `FACULTY`, `STUDENT` (was `USER`/`ADMIN`).
- `POST /api/auth/register` accepts `"role": "FACULTY" | "STUDENT"` (missing → FACULTY;
  anything else → 400 "Choose Faculty or Student."). ADMIN still only via `ADMIN_EMAILS`.
- Startup (`AdminBootstrap`): existing `USER` accounts are converted to `FACULTY`.
  New entity default role = FACULTY.
- New `config/DemoUserSeeder.java` + `app.demo-users-password=${DEMO_USERS_PASSWORD:}`:
  when set, creates (if missing) `admin@campuspulse.local` (ADMIN),
  `faculty@campuspulse.local` (FACULTY), `student@campuspulse.local` (STUDENT) with that
  password. Existing accounts are never changed. Unset = nothing happens.
Frontend
- Register: "I am Faculty / Student" choice, sent as `role`.
- After login the colour mode follows the role (student → teal, faculty → indigo);
  only admins see the Faculty/Student switch.
Infra
- `docker-compose.yml` and `.env.example`: `DEMO_USERS_PASSWORD`.
The password is never stored in the repository.

## How to test
1. Start backend with `$env:DEMO_USERS_PASSWORD = "<your demo password>"` → log shows
   "Created demo account …" three times (only the first time).
2. Log in as admin@campuspulse.local → mode switch visible, Tools → Job catalog visible.
3. faculty@campuspulse.local → indigo, no mode switch, no Job catalog.
4. student@campuspulse.local → teal, no mode switch, no Job catalog.
5. Register a new account choosing Student → logs in teal.
6. Existing older accounts still log in (now FACULTY).

## Risk / rollback
Never set DEMO_USERS_PASSWORD on a shared/real server. Revert the commit to undo
(converted roles stay FACULTY, which behaves like the old USER).
