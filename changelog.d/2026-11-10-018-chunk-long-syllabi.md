# Long syllabi are structured part by part, then merged
- Type: fix (AI quality)
- Area: backend
- Commit: see `git log --grep "Split long syllabi"`

## Why
The whole syllabus text went to the AI in one call with an 8,000-token answer limit.
Multi-subject or long PDFs got cut off mid-JSON; the "repair JSON" call cannot recover
missing content, so processing failed or topics were lost.

## What changed
- New `service/ai/SyllabusChunker.java`:
  - `split(text)`: text up to 12,000 characters → one part (same as before). Longer →
    parts ≤ 12,000 characters, cut at line breaks, preferring lines that start a section
    ("Module 3", "Unit II", "Course code…") once a part is 60% full.
  - `merge(parts)`: same subject name (or code) → one subject; a unit that continues
    across parts (same number + name) gets its topics joined; duplicate topic names dropped.
- `SyllabusAiService.structureSyllabus`: one AI call per part with a "part i of n" note,
  per-part results parsed without strict checks, the merged result validated once.
- Test: `SyllabusChunkerTest` (short text, split at headings within limit and no text
  lost, merge of subject + continued unit).

## How to test
1. Short syllabus (1–3 pages): behaves exactly as before (backend log has no "split into").
2. Long or multi-subject PDF (10+ pages): log shows "Syllabus text split into N parts";
   Curriculum shows all subjects/units; no unit appears twice.

## Risk / rollback
A subject named slightly differently in two parts may appear twice — rename in the
curriculum editor (Phase 5) or re-process. Revert the commit to undo.
