# 062 — Account section and password change on the student profile

## What changed
- My profile (students) now starts with an **Account** card: name, email, role, roll number / USN, college,
  department and programme (from `/api/auth/me`, set by the HOD / college admin).
- **Change password** button on that card: current password, new password (8+ characters), repeat; uses the
  existing `POST /api/auth/change-password`. Until now students could only change it on the forced
  one-time-password screen.
- New `frontend/src/components/AccountCard.jsx`; README documents `SELF_REGISTRATION` (061).
- Frontend lint and build pass; 146 backend tests pass.

## How to test
Sign in as a student, open My profile: the Account card shows your college and department. Change password,
sign out, sign in with the new password.
