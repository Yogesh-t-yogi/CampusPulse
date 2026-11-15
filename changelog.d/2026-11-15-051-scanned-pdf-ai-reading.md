# 051 — Read scanned PDF pages with the Llama API (no OCR install) (plan 14, Phase C)

## What changed
- Upload reads the **text layer of each page** (PDFBox, free). Pages that are empty or garbled
  (< 80 visible characters, or mostly unreadable symbols) are marked for AI reading.
  `PDF_AI_MODE=always` sends every page; `off` never does.
- Those pages are read during processing (background): page → JPEG (150 dpi, ≤ 2000 px) →
  `LlmService.generateFromImages` → first provider with a vision model (**Llama → Gemini → Groq**, change 050).
  If every AI fails: Tesseract **only if installed** (no longer required, no hard-coded Windows path).
- 3 pages in parallel (`PDF_AI_PARALLEL`), max 60 per upload (`PDF_AI_MAX_PAGES`, rest "skipped"),
  waits and retries when all providers are rate limited.
- **Cache** by file SHA-256 + page (`pdf_page_text`): uploading / re-processing the same PDF never calls AI again.
- The PDF is kept in `syllabus_file` with the page texts while pages wait; the result summary
  (pages with text / read by AI / OCR / unreadable) is saved there.
- Progress: "Reading scanned pages with AI: 4 of 12 done…" then the usual extraction steps.
  Upload page shows "12 pages: 9 with text, 3 read by AI" (warning if some page could not be read).
- A fully scanned PDF with no AI vision key → clear error telling which key to add.
- Resume match uses the same reading for scanned resumes (up to 5 pages, right away).
- **Migration V9** (`syllabus_file`, `pdf_page_text`); backend log must say "Database schema at version 9".
- Tests: `ScannedPdfReadingTest` (7, builds a real 2-page PDF with a text page and an image-only page),
  `PageMergeTest`. 73 backend tests pass.

## How to test
1. Restart the backend (start-dev.cmd) → "Database schema at version 9".
2. Upload a normal PDF → works as before, no AI page reading.
3. Upload a scanned PDF (or a photo-to-PDF) with `LLAMA_API_KEY` (vision model) or `GEMINI_API_KEY` set →
   progress shows pages being read → topics extracted; the summary line shows "read by AI".
4. Re-process the same syllabus → fast (pages come from the cache).
5. Remove all AI keys and upload a scanned PDF → clear "add LLAMA_API_KEY or GEMINI_API_KEY" error.

## Risk
Medium: upload/processing path changed. Text PDFs follow the old path (all pages have text → no file stored).
Scanned pages use AI quota: about one request per scanned page.
