# 066 — Students edit their own profile

## What changed
- `PUT /api/auth/me` (`ProfileService`): everyone can change their name. Students can also change their
  roll number / USN, department, programme and batch year.
  - Department must be in the student's own college; changing it clears the programme.
  - Programme must belong to the chosen department.
  - USN: 3–20 letters, digits, `-` or `/`, stored upper-case, unique within the college.
  - Batch year between 2000 and the current year + 6.
  - Email and role stay with the HOD / college admin (email is the login).
- `/api/auth/me` now also returns `batchYear`.
- My profile > Account card: "Edit profile" (name, USN, department, programme, batch year; programme list
  follows the department) next to "Change password". Saved details show at once across the app.
- `ProfileServiceTest` (6 tests). 157 backend tests pass; frontend lint and build pass.

## How to test
Restart the backend. As a student: My profile > Edit profile, change USN / department / programme, save.
Try a USN another student has: refused. A HOD sees the new department on the Users page.
