# 063 — More backend tests; PDF_AI_MODE=off honoured for scanned pages

## What changed
- `ScannedPageReader`: with `PDF_AI_MODE=off` scanned pages are never sent to LlamaParse or the vision
  models (Tesseract only, if installed); `canRead()` reflects that.
- New tests: `TenantIsolationTest` (one college cannot read another's data), `LlamaParseAttributionTest`
  (pages read by LlamaParse are reported as such), shared test defaults in `support/AppDefaults`.
- Extended tests: `RateLimitFilterTest`, `LlmServiceFallbackTest`, `ReportExportServiceTest`,
  `HybridMatchingTest`, `StudentImportServiceTest`, `OutcomeServiceTest`, `RoadmapNormalizeTest`,
  `ScannedPdfReadingTest`.
- 146 backend tests pass.
