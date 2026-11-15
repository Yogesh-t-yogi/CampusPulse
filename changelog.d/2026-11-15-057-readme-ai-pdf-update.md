# 057 — README brought up to date (changes 048–056)

- Stack: chat AI is Gemini → Groq with fallback; scanned PDFs read by LlamaParse → vision models →
  Tesseract only if installed (no OCR install needed). PDFBox only in the backend stack line.
- Setup: no Tesseract prerequisite; settings table lists `GEMINI_API_KEY`/`GROQ_API_KEY` (at least one),
  `LLAMA_CLOUD_API_KEY`, `PDF_AI_MODE`; points to `.env.example` for models/order/tier.
- Schema check text: version 9; migrations V1..V9.
- New section "How a syllabus PDF is read" (+ the `/api/health/ai-providers` check).
- Features: built-in 20-role catalog + 35-role CSV, report printing.
- Troubleshooting: provider errors, unreadable PDFs (LlamaParse region), retired model names (HTTP 404).
Docs only.
