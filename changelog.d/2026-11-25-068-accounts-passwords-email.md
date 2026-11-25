# 068 — Account rules: default password, no sign-up page, 100 per import, emails, isolation fixes

## What changed
1. **Default first password** `DEFAULT_USER_PASSWORD` (8+ characters): used for accounts a HOD / college
   admin creates, imports or resets. Empty: random one-time password per account (as before). Always must
   be changed at first sign-in.
2. **Accounts only by HOD / college admin.** The platform admin (`ADMIN_EMAILS`) is created at startup
   with the default password (`AdminBootstrap`), so the sign-up page is no longer needed.
3. **Sign-up removed from the login page** (`Register.jsx`, `auth/registration.js` deleted). The login
   page says the HOD or college admin creates accounts. `POST /api/auth/register` refuses everyone unless
   `SELF_REGISTRATION=true`.
4. **College isolation:** `GET /api/outcomes/report` now checks the job is accessible (a user could read
   another college's job skills through it). Import says "This email is already in use." instead of
   revealing that the email belongs to another college. Rest of the API audited and already scoped.
5. **Passwords:** HOD / admin reset (existing) now uses the default password and emails it. New
   **My account** page for HOD, admin, faculty and platform admin: edit name and change password
   (students already have it in My profile).
6. **Bulk creation capped at 100 people per import** (`StudentImportService.MAX_ROWS`).
7. **Email service** `AccountEmailService` (spring-boot-starter-mail): welcome email with sign-in link
   and first password on create / import, and on password reset. Works with any SMTP plan
   (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `APP_URL`); without
   `MAIL_HOST` nothing is sent. Mails are sent in the background. The password dialog says whether an
   email went out.
- Tests: `AccountCreationRulesTest` (4), `SelfRegistrationTest` updated. 161 backend tests pass; frontend
  lint and build pass.

## How to test
Restart the backend (new dependency: run `mvnw` once). Set `DEFAULT_USER_PASSWORD` in `.env`. Login page
has no "Create account". As HOD: Users > add a student; the dialog shows the default password. Sign in as a
faculty member: My account > Change password.
