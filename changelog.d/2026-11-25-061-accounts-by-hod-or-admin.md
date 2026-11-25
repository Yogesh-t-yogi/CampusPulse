# 061 — Accounts are created by the HOD or college admin

## Why
Self sign-up created users with no department (only a college code, often blank = Default College), so new
students and staff saw nothing until an admin fixed them. The HOD / college admin screens already create
accounts with role, department and programme set, a one-time password, and Excel/CSV bulk import.

## What changed
- New setting `app.self-registration` (`SELF_REGISTRATION`, default `false`).
  - `false`: `POST /api/auth/register` refuses everyone except the `ADMIN_EMAILS` (first-time platform admin
    setup) with "Accounts are created by your HOD or college admin…".
  - `true`: the old open sign-up (Faculty / Student + college code).
- `GET /api/auth/registration` (public) returns `{ "open": true|false }`.
- Sign-in page: when closed, says "No account yet? Your HOD or college admin creates it for your department."
  with an "Admin setup" link; the sign-up page then shows "Platform admin setup" without role / college code.
- `.env.example` documents `SELF_REGISTRATION`.
- `SelfRegistrationTest` (3 tests). 146 backend tests pass; frontend lint and build pass.

## How to test
Restart the backend. Sign-in page shows the HOD / admin note. Registering a student email is refused with the
message above. As HOD: Users > add a student (department is set) or import the Excel template; the student
signs in with the one-time password and must change it. Set `SELF_REGISTRATION=true` to reopen sign-up.
