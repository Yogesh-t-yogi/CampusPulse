# 046 — UI pass: sign-in, Choose job, Report tabs, phone layout, CSS clean-up (Phase 6.7)

## What changed
- **Login / Register** rebuilt on the theme (`pages/auth/AuthLayout.jsx`, `auth.css`): brand panel + form card,
  lucide icons, show/hide password, clearer "backend not running" message, dark mode works.
  Register still sends role + college code (blank = default college); same API.
- **Choose job**: new `pages/ChooseJob.jsx` replaces `JobMarket.jsx` (deleted; its insights/resume/catalog
  sections already live on their own pages). Card grid with top skills, search by role/company/skill,
  pagination, "Paste a job description" drawer, admin/own-job "Re-extract skills" icon.
- **Report tabs** rebuilt in `pages/report/ReportTabs.jsx`, replacing `Dashboard.jsx`, `GapAnalysis.jsx`,
  `AIRecommendations.jsx` (deleted):
  - Overview: stat cards, coverage donut, biggest gaps, strongest matches.
  - Skill by skill: status filter chips, search, closest topic + how it matched, similarity bar.
  - Recommendations: one card per skill with priority/status, curriculum change (staff), student advice,
    roadmap steps and links (role rules unchanged via `can()`).
- **Phone layout**: grids no longer overflow narrow screens, tables scroll sideways, drawers full width,
  header actions wrap, sidebar nav becomes one scrollable row under 640px.
- **App.css**: 624 unused rules removed by a scan of class names used in the code (5,682 → 1,198 lines).

## How to test
1. Sign out → Login and Register pages, light and dark, and at phone width.
2. Choose job: search, page, paste a JD, use a role.
3. Report: Overview / Skill by skill (filters) / Recommendations as faculty and as student.
4. Narrow the window to ~375px: Home, Syllabi, Report, Users should not scroll sideways.
5. Look around older pages (Curriculum, Upload, Market insights, Resume match) for missing styles — if
   anything looks unstyled, it is from the CSS clean-up; tell me the page.

## Risk
Medium (visual only). Old pages share App.css; removal was by class name, so a style used only through a
dynamic name could be lost (checked the known dynamic ones).
