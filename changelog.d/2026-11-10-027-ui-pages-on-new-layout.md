# UI upgrade part 2: workflow pages on the new layout
- Type: feature (UI)
- Area: frontend
- Commit: see `git log --grep "UI upgrade part 2"`

## Why
After part 1, Upload, Curriculum, Choose job, Market insights, Resume match and Report
still used their own old banners, so the app looked like two products.

## What changed
- All of them now start with the shared `PageHeader` (step label, title, one-line
  description, actions on the right).
- **Upload syllabus**: two-column layout — upload card + "What happens next" steps card.
- **Review curriculum**: title = syllabus name; header actions **Edit curriculum /
  Done editing** (moved from the toolbar) and **Choose a job** (next step); old banner and
  summary tiles replaced by StatCards (subjects, units, topics).
- **Choose job / Market insights / Resume match**: old hero replaced by PageHeader
  (copy per section).
- **Report**: header shows the job as title and the syllabus in the description; Re-run
  analysis and Export PDF as header actions (hidden in print).
- Spacing rule for new headers inside older page containers (`ui.css`).

## How to test
Walk Upload → Review curriculum → Choose job → Report and Tools pages in light and dark
mode: same header style everywhere; "Choose a job" on Curriculum goes to Choose job;
Edit curriculum still works; Re-run and Export PDF on Report still work.

## Risk / rollback
Visual only. Inner sections of these pages keep their previous styling (tokenised colours)
and get refined page by page later. Revert the commit to undo.
