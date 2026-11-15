# 045 — Report history and syllabus versions (Phase 5.3)

## What changed
- **Migration V8** (`V8__report_history_syllabus_versions.sql`, idempotent):
  - new table `report_snapshot` (one row per computed report: coverage, counts, algo version, full JSON, time);
  - `syllabus.previous_version_id` + `syllabus.version_no` (default 1).
  - `SchemaVersionCheck.EXPECTED_VERSION` = 8 → backend log should say "Database schema at version 8".
- Every time a gap report is computed or re-run, a snapshot is saved (`AnalysisResultService`). Snapshots
  survive curriculum edits / re-processing, so coverage history is kept.
- Upload accepts `revisionOf=<syllabusId>` (needs manage rights on the old one): the new upload keeps the
  old name, department, program, semester and sharing, links to it, and gets version + 1.
- New endpoints (`HistoryController`, `HistoryService`), read access required on every syllabus involved:
  - `GET /api/syllabus/{id}/versions` — this version and earlier ones;
  - `GET /api/syllabus/{id}/history?jobId=` — coverage runs over time across all versions;
  - `GET /api/syllabus/{id}/diff?against=<older>&jobId=` — topics added/removed per subject
    (case/punctuation-insensitive), coverage before/after and skills whose status changed.
- Syllabi list returns `versionNo`; UI shows a "v2" badge and an "Upload new version" action.
- Upload page shows a "New version of …" notice when opened with `?revisionOf=`.
- Report: new **History** tab — coverage line chart + runs table, and "Compare syllabus versions".
- Tests: `HistoryServiceTest` (topic diff, skill status changes).

## How to test
1. Restart the backend (start-dev.cmd) → "Database schema at version 8".
2. Open a report and click Re-run twice → History tab shows the runs.
3. Syllabi → "Upload new version" on a syllabus → upload an edited PDF → it appears as v2.
4. Make v2 active, choose the same job, open Report → History → Compare against v1.

## Risk
Migration only adds a table and two nullable/defaulted columns. Old reports have no history until re-run.
