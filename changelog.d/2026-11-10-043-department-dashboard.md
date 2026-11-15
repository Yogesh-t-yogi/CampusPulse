# 043 — Department / college dashboard (Phase 6.6)

## What changed
- Backend: `GET /api/dashboard?departmentId=&jobIds=` (`DashboardController`, `DashboardService`).
  - Scope: HOD/faculty → own department; ADMIN/SUPER_ADMIN → whole college or one chosen department; students → 403.
  - Returns syllabus counts, average coverage, a coverage grid (up to 12 processed syllabi × 5 benchmark roles),
    the 12 most common gap skills, popular student target roles, readiness bands and a per-student table.
  - Benchmark roles: chosen ones, else the most targeted by students, else catalog roles with most skills.
  - Uses saved analysis results, so the first load may take a while; later loads are fast.
- `StudentService.readinessSummary(user)`: target roles, best and average readiness, skills learned.
- Frontend: new page `pages/DepartmentDashboard.jsx` at `/dashboard-department` ("Dashboard" in the sidebar, staff only):
  stat cards, coverage grid, common gaps, popular roles, readiness bands, searchable student table,
  department selector (admins) and a benchmark-role picker drawer (up to 5).

## How to test
1. Log in as HOD or faculty → Dashboard shows your department only.
2. Log in as college admin → switch between "Whole college" and a department.
3. Benchmark roles → pick 2–3 jobs → Apply; "Automatic" resets.
4. Student login must not see the Dashboard entry.

## Risk
Read-only. The first load computes missing reports (Groq/AI service needed).
