# Bulk student onboarding from Excel/CSV + forced password change (Phase 6.5, backend)
- Type: feature
- Area: backend (database, API)
- Commit: see `git log --grep "Add bulk student onboarding backend"`

## Why
Colleges onboard hundreds of students at once; owner chose Excel credentials (no email).

## What changed
- `pom.xml`: Apache POI `poi-ooxml` 5.4.1 (Excel). Restart with `mvnw` so the runner picks it up.
- `V5__student_onboarding.sql`: `app_user.roll_number`, `batch_year`,
  `must_change_password` (default false); roll number unique per college.
  `SchemaVersionCheck.EXPECTED_VERSION` = 5.
- New `StudentImportService` + endpoints under `/api/admin/users` (SUPER_ADMIN/ADMIN/HOD):
  - `GET /import/template` → .xlsx: "Students" (headers + 2 example rows), "Codes" (this
    college's department/program codes), "How to fill".
  - `POST /import?apply=false|true` (multipart `file`, .xlsx or .csv, ≤5 MB, ≤2,000 rows) →
    `{applied, created, updated, errors, rows:[{row, name, email, rollNumber, department,
    program, action CREATE|UPDATE|ERROR, message, temporaryPassword}]}`.
    Columns: name*, email*, department_code*, roll_number, program_code, regulation,
    batch_year, role (STUDENT default / FACULTY). Checks: required fields, email format,
    duplicates in file (email, roll), unknown department/program codes, ambiguous regulation,
    batch year, roll used by someone else, email in another college or belonging to HOD/admin,
    HOD only into own department, role-granting rules. apply=true creates new people with a
    one-time password (must change at first sign-in) and updates existing ones (password
    unchanged); error rows are skipped.
  - `POST /credentials-sheet` (JSON list of {name, email, rollNumber, temporaryPassword}) →
    .xlsx built in memory from the import response; nothing stored.
- Admin-created users and password resets now also set `mustChangePassword`.
- Users API returns/accepts `rollNumber`, `batchYear`; returns `mustChangePassword`.
- Auth: login response and `/me` include `mustChangePassword`; new
  `POST /api/auth/change-password {currentPassword, newPassword}` (≥8 chars, must differ)
  clears it. `JwtAuthenticationFilter` answers every non-`/api/auth/**` request with 403
  `{"code":"PASSWORD_CHANGE_REQUIRED"}` until the password is changed.
- Test: `StudentImportServiceTest` (preview validation without saving, apply creates with
  one-time password, HOD limited to own department, template sheets + template re-import).

## How to test
Frontend part follows (037). With curl as admin@: download template, fill 3 rows, POST
`/import` (preview) then `/import?apply=true`; sign in as a new student → any API call except
/api/auth returns PASSWORD_CHANGE_REQUIRED until `/api/auth/change-password`.

## Risk / rollback
Schema change V5 (additive). Revert the commit to undo code.
