# Role-based access for Admin, Faculty and Student
- Type: feature (security)
- Area: backend, frontend
- Commit: see `git log --grep "Add role-based access"`

## Why
Roles existed (change 015) but only changed colours; the admin top bar had a
Faculty/Student tab that changed colours only. Owner asked for real separated features.

## What changed
Access table: `_claude/plans/07-rbac.md` (local) — summary:
- Everyone: upload syllabus, curriculum, choose job, report, market insights.
- Faculty: report shows "Curriculum improvement"; no resume match.
- Student: report shows "Student action plan" + learning roadmap; has Resume match.
- Admin: everything + Job catalog + new Users page.
Backend
- `SecurityConfig`: `/api/admin/**` → ADMIN; `/api/resume/**` → STUDENT or ADMIN.
- New `controller/admin/AdminUserController`: `GET /api/admin/users`,
  `PUT /api/admin/users/{id}/role {"role": "ADMIN|FACULTY|STUDENT"}` (not your own).
Frontend
- New `src/permissions.js` (page/feature → roles), used by Sidebar and App.
- Sidebar groups: ANALYSIS (workflow), TOOLS (Market insights, Resume match*),
  ADMIN (Job catalog, Users) — items shown per role.
- Opening a page your role can't use shows "Not available for your role".
- New pages: `/resume` (Resume match, split from Market insights), `/users` (`Users.jsx`).
- `AIRecommendations` gets `role`: hides curriculum block for students, hides action plan
  and roadmap for faculty.
- Top bar: Faculty/Student tab removed; shows "Name · Role" badge. Colour follows role.

## How to test (restart backend; log out and in)
1. student@campuspulse.local: sidebar has Resume match, no ADMIN group; report
   Recommendations shows action plan + roadmap, no curriculum block; top bar "Demo Student · Student".
2. faculty@campuspulse.local: no Resume match, no ADMIN group; Recommendations shows the
   curriculum block only; opening http://localhost:5173/resume → "Not available for your role";
   `POST /api/resume/match` with faculty token → 403.
3. admin@campuspulse.local: ADMIN group with Job catalog + Users; Users lists accounts,
   changing a role saves (your own row is read-only); that user sees the new role after re-login.

## Risk / rollback
Faculty lose resume matching (was available to all). Revert the commit to undo.
