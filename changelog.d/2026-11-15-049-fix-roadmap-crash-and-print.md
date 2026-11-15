# 049 — Fix "How to learn it" blank screen and blank report printing (plan 14, Phase A)

## Cause
- Learning plan → "How to learn it" rendered the AI roadmap JSON as-is. When the model returned an object
  (e.g. `"project": {"title":..,"description":..}`) React crashed, and with no error boundary the whole
  app went blank. The bad answer was also cached, so it kept crashing for that skill.
- Report → Print used the browser's print of the app frame (sticky full-height sidebar, flex layout,
  dark colours, auto-sized charts) → blank or clipped pages.

## What changed
- Backend `StudentService.normalizeRoadmap`: any AI shape → `{summary, steps:[{title, action, hours}], project}`
  with plain strings (objects joined, `steps`/`roadmap`/`learningRoadmap` accepted, hours parsed from text,
  max 8 steps). Unusable answers are not cached; old bad cached rows are dropped and regenerated.
  Prompt now asks for plain strings.
- Frontend `LearningPlan.jsx`: renders only text; missing steps show a warning and reopening retries.
- New `components/ErrorBoundary.jsx` around every page: a crash shows "Something went wrong on this page"
  with Reload instead of a blank screen; moving to another page clears it.
- Report "Print" now opens the server-made PDF (044) in a new tab to print; if that fails it falls back to
  browser print. New `@media print` styles: hide sidebar/header/tabs/buttons, static layout, light colours,
  fixed-width charts, no breaks inside cards.
- Tests: `RoadmapNormalizeTest` (3). 55 backend tests pass; lint + build pass.

## How to test
1. Student → Learning plan → "How to learn it" on several skills (including one that broke before).
2. Report → Print → a new tab with the PDF opens → print it. Also try Ctrl+P on the report page.
3. Allow pop-ups for localhost if the browser blocks the new tab.

## Risk
Low. Print CSS only applies when printing.
