# Bulk onboarding screens and first-sign-in password change (Phase 6.5, frontend)
- Type: feature (UI)
- Area: frontend
- Commit: see `git log --grep "Add bulk onboarding screens"`

## What changed
- **Users → Import from Excel** (new `components/StudentImport.jsx`):
  1. Download template (.xlsx with your college's department/program codes).
  2. Choose .xlsx or .csv → **Check file**: preview table per row (New / Update / Problem with
     the reason), counts; nothing saved.
  3. **Import N rows** → result + **Download logins** (credentials .xlsx with one-time
     passwords; shown once).
- Users table: roll number next to the email; "Waiting for first sign-in" badge until the
  person sets a password. Add/Edit user: Roll number and Batch (joining year) for students.
- **First sign-in**: new `pages/ChangePassword.jsx` — anyone with a one-time password (created
  by an admin, imported, or reset) must set a new password before the app opens. Triggered from
  the login response, `/me`, or any `PASSWORD_CHANGE_REQUIRED` answer (`apiClient.js` event).
  Sign out is available there.

## How to test (restart backend with mvnw → Flyway V5; demo college from 031)
1. admin@ → Users → Import from Excel → Download template; fill 3 students for CSE / program CSE
   (one with a wrong department code). Check file → 2 New, 1 Problem. Import → Download logins.
2. Sign in as one of them with the one-time password → "Set your own password" screen; wrong
   one-time password → error; mismatched new passwords → error; save → app opens.
3. Re-import the same file → those rows show "Update" (password unchanged).
4. hod@ → Import: rows for another department → Problem ("Only the college admin or this
   department's HOD…").
5. Users → Reset password on someone → they must change it at next sign-in.

## Risk / rollback
UI only on top of 036. Revert the commit to undo.
