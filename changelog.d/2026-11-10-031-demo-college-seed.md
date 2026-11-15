# Demo college for the demo accounts
- Type: chore (local demo data)
- Area: backend
- Commit: see `git log --grep "Seed a demo college"`

## Why
Owner asked to seed a proper college for admin@campuspulse.local so the college admin,
HOD, faculty and student demo accounts can be tried end to end.

## What changed
`DemoUserSeeder` (only when `DEMO_USERS_PASSWORD` is set):
- Ensures **CampusPulse Demo College** (code `DEMO`, Chennai) with departments CSE, IT, ECE, MCA
  and programs: B.E. CSE (2021, 8 sem), B.Tech IT (2021, 8), B.E. ECE (2021, 8), MCA (2023, 4).
- Accounts: superadmin@ (platform admin), admin@ (**college admin of DEMO**), hod@ and faculty@
  (CSE), student@ (CSE, B.E. CSE program).
- Existing demo accounts are moved into DEMO only while they are still in Default College
  (later manual changes are kept); their syllabi move with them. Passwords are never reset.
- Idempotent: safe on every start.

## How to test
Restart backend with `DEMO_USERS_PASSWORD`. Log: "Created demo college DEMO", "Placed demo
account admin@campuspulse.local (ADMIN) in DEMO" etc. Sign in as admin@ → header chip
"CampusPulse Demo College"; Organisation shows 4 departments and 4 programs; Users shows the
HOD, faculty and student with their department/program.

## Risk / rollback
Demo-only. Revert the commit to undo (created rows stay; delete the DEMO college to remove).
