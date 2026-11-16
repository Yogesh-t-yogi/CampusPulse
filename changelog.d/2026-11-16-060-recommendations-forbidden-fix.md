# 060 — Fix "Forbidden" on Report > Recommendations

## Cause
A security rule added in the working copy limited `/api/recommendations/**` to SUPER_ADMIN, ADMIN, HOD and
FACULTY, copying the frontend's `curriculumRecommendations` flag. That flag only hides the curriculum-change
part; every role (including students) opens Report > Recommendations and students see the student advice.
So students got HTTP 403 "Forbidden".

## What changed
- `SecurityConfig`: `/api/recommendations/**` allowed for all five roles (SUPER_ADMIN, ADMIN, HOD, FACULTY,
  STUDENT). The `/api/student/roadmap` rule (STUDENT, ADMIN, SUPER_ADMIN) from the same change is kept.
- `SecurityRulesTest` (route/role rules through the real filter chain, 22 tests) committed; the TC-02
  recommendation case now expects 200 for students (`tc02_recommendationsAreOpenToEveryRole`).
- 143 backend tests pass.

## How to test
Restart the backend. As a student: Report > Recommendations lists recommendations (student advice, no
curriculum change). As faculty: also shows the curriculum change.
