# Split Job Market into Choose job, Market insights and admin Job catalog
- Type: feature (flow)
- Area: frontend, backend
- Commit: see `git log --grep "Split job market"`

## Why
One page did five things (choose job, paste JD, CSV import, skill-demand chart, resume
match); the main action was buried at the bottom. Catalog tools are admin-only since
change 001, but every user still saw them.

## What changed
- Backend: new `GET /api/auth/me` → `{id, name, email, role}` (logged-in users).
- `JobMarket.jsx` takes `section`:
  - `choose` (`/jobs`, step 3 "Choose job"): paste a job description + catalog list.
  - `insights` (`/insights`, sidebar "Tools → Market insights"): most-requested-skills chart
    + resume matching.
  - `catalog` (`/catalog`, sidebar "Tools → Job catalog", **admins only**): CSV import +
    catalog list.
  Header text changes per section.
- "Extract skills with AI" button is shown only for admins, or on the user's own
  pasted-description jobs (the backend already refused other cases).
- `App.jsx` loads `/api/auth/me` after login; `isAdmin` passed to Sidebar and JobMarket.
- Sidebar: workflow items use the step labels (Upload syllabus, Review curriculum,
  Choose job, Report) and a new "TOOLS" group.

## How to test
1. Normal user: sidebar Tools shows only "Market insights"; Choose job shows paste form +
   catalog, no CSV import; catalog jobs have no "Extract skills with AI" button; your own
   pasted job does.
2. Market insights: chart + resume matching, no job list.
3. Admin (email in `ADMIN_EMAILS`, restart backend, log in again): Tools also shows
   "Job catalog" with CSV import; extract button on every job.
4. `GET /api/auth/me` with your token returns your role.

## Risk / rollback
Admin status is read at login; after changing ADMIN_EMAILS log out and in again.
Revert the commit to undo.
