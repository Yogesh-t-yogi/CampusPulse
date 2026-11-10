# Flow fixes: one step order, no auto-picked job, real AI status, retry without reload
- Type: fix (flow)
- Area: frontend, backend
- Commit: see `git log --grep "Unify navigation"`

## Why
- Sidebar and step bar listed pages in different orders, and the sidebar let you open
  pages whose inputs were missing.
- On login the first job was selected automatically, so "Choose job" looked done.
- Sidebar always said "AI Engine · Connected" (hard-coded).
- "Try again" reloaded the whole browser page.
- Syllabus dropdown showed "(ID: 14)".

## What changed
- New `frontend/src/workflow.js`: single step list + availability rules, used by both
  `Sidebar.jsx` and `WorkflowSteps.jsx`. Order: Upload → Curriculum → Choose job →
  Gap analysis → Recommendations → Dashboard. Locked items are greyed with a tooltip;
  finished steps show a ✓ in the step bar.
- `App.jsx`: no automatic job selection (a remembered choice is still restored);
  choosing/creating a job goes to Gap Analysis (curriculum is now reviewed before);
  dropdown shows upload date instead of ID.
- Sidebar status light now real: calls new `GET /api/health/services` every 60 s →
  green "Connected", amber "AI service offline" / "Groq key missing", red "Backend offline".
- Backend `HealthController`: `/api/health/services` (logged-in users) checks the AI service
  `/health` (3 s timeout) and whether a Groq key is set (does not call Groq).
- Gap Analysis, Recommendations, Dashboard, Curriculum: "Try again" re-fetches data.

## How to test
1. New login with no job chosen: sidebar shows Gap Analysis / Recommendations / Dashboard
   locked until you pick a job in Choose job (Job Market).
2. Pick a job → you land on Gap Analysis; step bar shows ✓ for upload, curriculum, job.
3. Stop the AI service → within a minute the sidebar shows amber "AI service offline".
4. Stop the backend, open Gap Analysis, start backend, click "Try again" → loads without
   a full page reload.

## Risk / rollback
Users used to land on Curriculum after choosing a job; now Gap Analysis. Revert to undo.
