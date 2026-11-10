# One Report page (Overview / Skill by skill / Recommendations) with Re-run
- Type: feature (flow)
- Area: frontend
- Commit: see `git log --grep "Merge gap analysis"`

## Why
Gap Analysis, AI Recommendations and Dashboard showed the same analysis on three
separate pages, each with its own header, and there was no way to force a fresh run.

## What changed
- New `src/pages/Report.jsx` at `/report`: one header (syllabus · job), buttons
  **Re-run analysis** (calls the report with `?refresh=true`, then reloads the tab) and
  **Export PDF** (browser print), and tabs: Overview (old Dashboard charts),
  Skill by skill (old Gap Analysis), Recommendations (old AI Recommendations).
  Selected tab is in the URL (`/report?tab=skills`).
- `GapAnalysis.jsx`, `AIRecommendations.jsx`, `Dashboard.jsx`: new `embedded` prop hides
  their own hero header / export row; embedded Dashboard no longer fetches
  recommendations (that tab does it), so opening Overview never calls Groq.
- Old URLs `/dashboard`, `/gap-analysis`, `/recommendations` redirect to the matching tab.
- Workflow is now 4 steps: Upload → Curriculum → Choose job → Report (sidebar + step bar).
- Re-run clears saved recommendations on the server; they regenerate (one Groq call)
  the next time the Recommendations tab opens.

## How to test
1. Choose a job → lands on Report, Overview tab with charts.
2. Switch tabs: Skill by skill shows the per-skill list; Recommendations loads (first
   time one Groq call, after that instant). Refresh keeps the tab.
3. Re-run analysis → button shows "Re-running…", then the tab reloads.
4. Open old link http://localhost:5173/gap-analysis → Report, Skill by skill tab.
5. Export PDF prints the current tab (tabs and buttons hidden in print).

## Risk / rollback
Old pages still exist as components (used inside the tabs). Revert the commit to undo.
