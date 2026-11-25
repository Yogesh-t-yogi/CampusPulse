# 064 — Faster recommendations

## Cause
The first load of Report > Recommendations sent every skill of the job (covered ones too) to the AI in one
request and asked for an explanation and a 3-step roadmap for each. One long answer took up to a minute or
more, and opening the tab twice could start two AI runs.

## What changed
- `AiRecommendationService`: covered (MATCHED) skills get a fixed note, no AI call. Partly covered and gap
  skills go to the AI in batches of 6, up to 3 batches at a time. Skills the AI skips get short generic
  advice. Output keeps the report's skill order. No AI call at all when nothing is missing.
- `AnalysisResultService.getRecommendations`: one AI run per syllabus x job at a time; a second request
  waits for it instead of starting another.
- Report Overview starts loading the recommendations in the background once the report is shown, so the
  Recommendations tab usually opens ready. Results are still saved; "Re-run analysis" refreshes them.
- `AiRecommendationBatchTest` (3 tests). 149 backend tests pass; frontend lint and build pass.

## How to test
Restart the backend. Open a report you have not opened before, wait on Overview a few seconds, then open
Recommendations. Previously saved recommendations are kept until "Re-run analysis".
