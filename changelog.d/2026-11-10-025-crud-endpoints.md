# Create / edit / delete endpoints for syllabi, jobs and users
- Type: feature
- Area: backend
- Commit: see `git log --grep "Add create, edit and delete endpoints"`

## Why
The UI upgrade needs real index / create / edit / delete actions. Before this you could not
rename or delete a syllabus, edit or delete a job, or create/delete a user.

## What changed
Syllabi (owner only)
- `GET /api/syllabus` now returns a light summary per syllabus:
  `{id, name, fileName, createdAt, processingStatus, processingMessage, subjectCount, topicCount}`
  (was the full entity incl. extracted text).
- `PUT /api/syllabus/{id}` `{name}` — rename.
- `DELETE /api/syllabus/{id}` — deletes curriculum, topic vectors and saved reports.
Jobs
- `POST /api/jobs` (ADMIN) now takes `{title, company, location, description, skills[]}` and
  adds a shared catalog job (was raw entity binding).
- `PUT /api/jobs/{id}` same body — edit + replace skills; `DELETE /api/jobs/{id}`.
  Allowed for your own private jobs, or any job for admins (same rule as change 001).
  Editing clears saved reports for that job.
- `JobPostingRepository.findAllByOwner_Id`.
Users (ADMIN, `/api/admin/users`)
- `POST` `{name, email, role}` → account with a one-time password returned once
  (`temporaryPassword`); 409 if the email exists.
- `POST /{id}/reset-password` → new one-time password.
- `DELETE /{id}` → deletes the account plus its own syllabi and private jobs
  (shared catalog jobs stay). Your own account can't be changed or deleted here.
- New `TempPasswords` (12 chars, no look-alike characters).

## How to test
1. Syllabi list response no longer contains `extractedText`.
2. Rename / delete a syllabus via the new Syllabi page (change 027) or curl.
3. Admin: create a user → response shows `temporaryPassword`; log in with it.
4. Admin: delete that user → their syllabi are gone; catalog jobs untouched.

## Risk / rollback
Delete is permanent (the UI asks for confirmation). Revert the commit to undo.
