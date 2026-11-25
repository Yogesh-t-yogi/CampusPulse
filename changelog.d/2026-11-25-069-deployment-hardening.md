# 069 — Hardening before deployment (review items 7–10)

## What changed
- **Deleting a user keeps shared syllabi.** Syllabi shared with the department or college move to the
  HOD / admin who deletes the account, with their reports, CO/PO and history. Only private syllabi and
  private jobs are deleted. The confirm dialog says so; the API returns `sharedSyllabiKept`.
- **Old logins end after a password change or reset.** New column `app_user.password_changed_at`
  (Flyway `V10__password_changed_at.sql`); the JWT filter refuses tokens issued before it.
  `POST /api/auth/change-password` returns a fresh token, which the app stores, so the person changing
  the password stays signed in while every other session (e.g. a stolen token) is closed.
- **Rate limits:** `POST /api/auth/change-password` is in the sign-in bucket (password guessing) and
  `POST /api/outcomes/syllabus/{id}/extract` in the AI bucket.
- **CO extraction no longer holds a database connection during the AI call.** The AI answer is read
  first; only saving the outcomes runs in a short transaction.
- Tests: `DeploymentHardeningTest` (3), `RateLimitFilterTest` extended. 164 backend tests pass; frontend
  lint and build pass.

## How to test
Restart the backend (Flyway adds the column). Sign in on two browsers, change the password in one: the
other is signed out on its next action. As HOD, delete a faculty member who shared a syllabus: it now
appears under your syllabi.
