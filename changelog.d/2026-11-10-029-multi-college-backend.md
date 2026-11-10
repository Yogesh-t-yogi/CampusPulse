# Multi-college backend: colleges, departments, programs, roles and scoping (Phase 6.2, part 1)
- Type: feature (security + data model)
- Area: backend (database, API)
- Commit: see `git log --grep "Add multi-college backend"`

## Why
One deployment must serve many colleges, each with departments and programs (MCA, BE CSE,
IT, ECE…), with roles Admin → HOD → Faculty (+ Student). Design: `_claude/plans/10-tenancy-design.md`.

## What changed
Database — `V3__institutions_departments_programs.sql`
- New tables `institution`, `department`, `program` (degree, code, regulation, semesters).
- New columns: `app_user.institution_id/department_id/program_id`,
  `syllabus.institution_id/department_id/program_id/semester`,
  `job_posting.institution_id` (NULL = global catalog).
- Creates **Default College** (code `DEFAULT`) with department **General** (`GEN`); all
  existing users and syllabi move there.
Roles
- New `SUPER_ADMIN` (platform) and `HOD`. `ADMIN` = college admin.
- `ADMIN_EMAILS` now grants **SUPER_ADMIN**. Existing ADMIN accounts stay college admins.
- Demo seeder adds `superadmin@campuspulse.local` and `hod@campuspulse.local`; demo accounts
  are placed in Default College / General.
- `TenantService`: acting college (super admin may switch with header `X-Institution-Id`,
  allowed by CORS), college-admin and department-manager checks, role-granting rule
  (only HOD+ manage accounts; admins may give their own role or lower, HODs only lower).
API
- New `/api/org/institutions` (super admin writes), `/api/org/departments` (college admin
  writes), `/api/org/programs` (college admin or that department's HOD writes); reads scoped
  to the acting college; responses include counts. Deleting a college with users → 409.
- `/api/auth/me` adds `institution`, `department`, `program` ({id, name, code}).
- Register takes `institutionCode` (blank = DEFAULT; unknown → 400).
- `/api/admin/users`: SUPER_ADMIN/ADMIN/HOD; lists the acting college (HOD: own department,
  faculty and students only); create/update accept `departmentId`, `programId`; new
  `PUT /api/admin/users/{id}`; nobody manages accounts above them.
- Jobs: visible = global catalog + acting college's catalog + own private jobs. Catalog
  changes: global → SUPER_ADMIN; college catalog → that college's ADMIN. New catalog jobs and
  CSV imports go to the importer's college (super admin → global). Skill-demand chart scoped
  the same way.
- Uploaded syllabi record the uploader's college and department.
Tests: `TenantServiceTest` (role granting, college admin, department managers — caught and
fixed a bug where faculty could grant the Student role), `JobPostingServiceAccessTest`
rewritten for the new catalog rules. 31 tests pass.

## How to test (restart backend with DEMO_USERS_PASSWORD set)
1. Log shows Flyway "Migrating schema … to version 3"; `SELECT * FROM institution;` → Default College.
2. `GET /api/auth/me` → institution Default College, department General.
3. superadmin@: `POST /api/org/institutions {"name":"ABC Engineering College","code":"ABC"}`.
4. admin@: create department CSE, program `{"departmentId":…,"name":"B.E. Computer Science and Engineering","degree":"BE","code":"CSE","regulation":"2021"}`.
5. hod@ (move hod into CSE first as admin): sees only CSE faculty/students in Users; can't create an HOD.
6. Register with college code ABC → new user lands in ABC; doesn't see Default College's catalog jobs.
Frontend for these screens comes in part 2.

## Risk / rollback
Schema change (V3). Rollback needs a DB restore or manual drop of the new columns/tables.
Existing single-college use is unchanged (everything lives in Default College).
