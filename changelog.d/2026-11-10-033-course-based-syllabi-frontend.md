# Course-based syllabi screens: program, semester, sharing (Phase 6.3, frontend)
- Type: feature (UI)
- Area: frontend
- Commit: see `git log --grep "Add course-based syllabi screens"`

## Why
UI for change 032.

## What changed
- **Upload syllabus**: Program and Semester pickers (programs of your department for HOD/
  faculty, whole college for admins; students fixed to their own program; semesters from the
  program's length) + a line explaining who will see it.
- **Syllabi** (rewritten): columns Syllabus (by you / by owner), Program · Semester, Shared
  (Department / Private), Status, Topics, Uploaded; filters Mine / Shared with me, program,
  status, search. Edit details dialog (name, program, semester, who can see it), Re-process
  and Delete shown only when you may change the syllabus (`canManage`).
- **Review curriculum**: "Edit curriculum" hidden on syllabi you can only read.
- `App.jsx` passes the signed-in user to Upload and Syllabi.

## How to test (backend 032, demo college from 031)
1. faculty@ → Upload: pick "B.E. Computer Science and Engineering", Semester 5, upload.
2. student@ (B.E. CSE) → Syllabi → filter "Shared with me" → sees it (Department badge);
   no edit/delete buttons; Open → no "Edit curriculum"; Report works.
3. hod@ → Syllabi → Edit details: change semester / make Private → student@ no longer sees it.
4. student@ uploads own syllabus → Program fixed, shows Private.

## Risk / rollback
UI only. Revert the commit to undo.
