# Compare one syllabus against several jobs (Phase 5.2)
- Type: feature
- Area: backend, frontend
- Commit: see `git log --grep "Add compare jobs"`

## Why
Colleges want to know how a syllabus prepares students for a group of roles, not one job
at a time.

## What changed
Backend
- New `GET /api/matching/semantic/report/multi?syllabusId=3&jobIds=1,4,7` (owner of the
  syllabus; each job must be accessible; 1–10 jobs).
- New `service/analysis/MultiRoleReportService`: reuses each job's saved gap report
  (computes only missing ones), then returns
  `{jobs: [{jobId, title, company, coverage, totalSkills}],
    skills: [{skill, status, topic, jobIds, jobCount}],  // most-demanded first
    combinedCoverage,   // MATCHED 1, PARTIAL 0.5, GAP 0, weighted by jobCount
    topGaps}`           // up to 10 GAP skills, most-demanded first
  Skills are merged case-insensitively ("SQL" = "sql").
- Test: `MultiRoleReportServiceTest` (merge + weighting + top gaps, job-count limits).
Frontend
- Report → new **Compare jobs** tab (`/report?tab=compare&jobs=1,4,7`): pick up to 10 jobs
  (filter box), Compare → combined coverage, top missing skills, and a skill × job grid
  (Matched / Partial / Gap chips, "—" where a job doesn't need the skill), per-job coverage
  in the column headers. New `src/pages/CompareJobs.jsx`; `App.jsx` passes the job list.

## How to test
1. Import the starter dataset (or have several jobs).
2. Report → Compare jobs → tick e.g. Java Backend Developer, Data Analyst, DevOps Engineer →
   Compare. First run may take a few seconds per job; repeat is instant.
3. Check: skills needed by more jobs are at the top; Docker-type gaps listed under Top missing
   skills; the URL keeps the selection (refresh shows the same comparison).
4. Selecting 11 jobs is blocked in the UI; API with 11 → 400.

## Risk / rollback
Additive. Revert the commit to undo.
