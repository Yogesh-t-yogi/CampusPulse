# CO/PO screens: program outcomes, course outcomes + mapping grid, outcomes report (Phase 6.4)
- Type: feature (UI)
- Area: frontend
- Commit: see `git log --grep "Add CO/PO screens"`

## What changed
- **Organisation → Programs → Outcomes** (new `components/ProgramOutcomes.jsx`, drawer):
  list POs/PSOs; **Add NBA POs** (PO1–PO12), Add PSO, edit, delete (admin / HOD).
- **Review curriculum → Course outcomes (CO) and CO–PO mapping** (new
  `components/CourseOutcomes.jsx`): per subject a table of COs (code, statement, Bloom level)
  with one column per PO/PSO; level picker – / 1 low / 2 medium / 3 high saves instantly;
  **Extract with AI** (or Re-extract, with confirm), Add CO, edit, delete. Read-only for people
  who can't change the syllabus. Hints when the syllabus has no program or the program no POs.
- **Report → Outcomes (CO/PO)** tab (new `pages/OutcomesReport.jsx`): bar per PO/PSO with
  coverage % for the selected job; a card per CO with the job skills it covers
  (matched/partial) and the missing skills that fit it best.
- Organisation CRUD sections accept extra row actions.

## How to test (backend 038, restart with mvnw → V6)
1. hod@ → Organisation → Programs → B.E. CSE → Outcomes → Add NBA POs → 12 rows; add PSO1.
2. faculty@ → upload + process a syllabus with program B.E. CSE → Review curriculum → Course
   outcomes → Extract with AI → COs per subject with suggested levels; change a level; edit a CO.
3. Choose a job → Report → Outcomes (CO/PO): PO bars + CO cards with skills and gaps.
4. student@ opening the same syllabus sees COs and the grid read-only.

## Risk / rollback
UI only on top of 038. Revert the commit to undo.
