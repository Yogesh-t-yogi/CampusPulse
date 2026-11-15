# Multi-college frontend: Organisation page, college switcher, users by department (Phase 6.2, part 2)
- Type: feature (UI)
- Area: frontend
- Commit: see `git log --grep "Add multi-college frontend"`

## Why
UI for change 029: manage colleges, departments and programs, place people in them, and
let the platform admin work in any college.

## What changed
- `permissions.js`: roles SUPER_ADMIN (Platform admin), ADMIN (College admin), HOD, FACULTY,
  STUDENT; new pages Organisation (SUPER_ADMIN, ADMIN, HOD) and Users (+HOD); Job catalog
  SUPER_ADMIN/ADMIN; `assignableRoles()` mirrors the backend rule.
- New **Organisation** page (`/organisation`, sidebar Admin group): tabs Colleges
  (platform admin), Departments (college admin edits), Programs (college admin or HOD for own
  department). Each: table with search and counts, Add/Edit drawer, Delete confirm.
- **Header**: college chip (college · department) for everyone; platform admin gets a
  **College** switcher. The choice is sent as `X-Institution-Id` (`apiClient.js`), the page
  reloads its data, and it is cleared on sign-in/out.
- **Users** (rewritten): Department · Program column; filters by role and department;
  Add/Edit drawer with role (only roles you may grant), department (HOD fixed to own),
  program (students, filtered by department); reset password; delete. HOD sees own department.
- **Register**: "College code" field (blank = default college).
- Badge colour for Platform admin.

## How to test (backend from 029 running, DEMO_USERS_PASSWORD set)
1. superadmin@campuspulse.local → Organisation → Colleges: add "ABC College" (code ABC).
   Header College switcher now lists it; switch to ABC → Users/Departments show ABC (empty).
2. admin@campuspulse.local → Organisation → Departments: add CSE; Programs: add
   "B.E. Computer Science and Engineering", BE, CSE, 2021.
3. Users → edit hod@ → role HOD, department CSE. Sign in as hod@: Users shows only CSE
   faculty/students; role list offers Faculty/Student only; Organisation → Programs editable for CSE.
4. Add a student with department CSE + program → one-time password → sign in with it.
5. Register a new account with college code ABC → header chip shows ABC.

## Risk / rollback
UI only on top of 029. Revert the commit to undo.
