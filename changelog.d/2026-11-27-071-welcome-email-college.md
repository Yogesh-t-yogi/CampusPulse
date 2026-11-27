# 071 — College name in the welcome email

- Welcome email (new account and import) now names the college: subject "Your CampusPulse account at
  <college>", and the body says "<college> has created a CampusPulse account for you" with College and Role.
- Roles are written in plain words (College admin, Head of department, Faculty, Student, Platform admin).
- Imported accounts get their actual role instead of "CampusPulse user".
- `WelcomeEmailTest`. 165 backend tests pass.
