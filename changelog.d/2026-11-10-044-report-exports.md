# 044 — Report exports: server PDF + Excel (Phase 5.4)

## What changed
- Backend: `GET /api/export/report/pdf?syllabusId=&jobId=` and `/api/export/report/excel?...`
  (`ReportExportController`, `service/export/ReportExportService`). Same access rules as the report.
  - PDF (A4, PDFBox standard fonts): college, title, date, coverage with a stacked bar
    (covered/partly/gap), every skill colour-coded with its topic, recommendations (if saved/available),
    NBA PO coverage (if COs exist). Text the font can't print becomes "?" instead of failing.
  - Excel (POI): sheets Summary, Skills, Recommendations, PO coverage.
  - Uses the saved gap report; missing recommendations / CO-PO are skipped, not errors.
- Frontend Report header: "Print" (old browser print), "Excel", "Download PDF".
- Test: `ReportExportServiceTest` (valid PDF with Unicode skills, valid xlsx).

## How to test
Open a report → Download PDF and Excel → open both; check numbers match the Overview tab.
Try a syllabus with COs to see the PO coverage section.

## Risk
New read-only endpoints only.
