# 058 — Faster exports, print without a blank tab, report page redesign

## Problems (owner)
- Excel / PDF download and Print took very long.
- Print opened another page that stayed blank.
- Report page needed clearer attributes and more visualisations.

## Causes
- Every export called `AnalysisResultService.getRecommendations`, which asks the AI to write recommendations
  when none are saved yet (tens of seconds, longer with provider fallback).
- Print opened an empty tab first, then waited for that slow PDF; on any failure the tab stayed blank.

## What changed
- `AnalysisResultService.savedRecommendations()` returns saved recommendations only; exports use it and never
  call the AI. The PDF says "Recommendations: not generated yet" when there are none, and now has a
  "How to read this report" block defining Covered, Partly covered, Gap, Similarity and the coverage formula.
- Print loads the server PDF into a hidden frame on the same page and opens the print dialog there (no new
  tab); falls back to the browser's print of the page.
- Report Overview redesigned (`pages/report/ReportTabs.jsx`):
  - coverage ring with an alignment band, a sentence with counts and shares, the coverage formula with the
    actual numbers, and a stacked share bar;
  - stat cards that state each rule (similarity ≥ 0.50 or named in a topic, 0.25–0.50, < 0.25);
  - "Similarity per skill" bar chart with threshold lines at 0.25 and 0.50 and a tooltip (status, score, topic);
  - "Similarity distribution" histogram; "How each skill was matched" chart (named in topic / by meaning / none);
  - biggest gaps, strongest matches with scores, and a "How to read this report" definitions panel.
- Skill by skill: "Matched by" column and the similarity value (0–1) next to the bar; rules in the card text.
- Recommendations: priority and status charts above the list.
- Test `ExportNoAiCallTest`: PDF and Excel never call `getRecommendations`; PDF contains the definitions.

## How to test
1. Open a report whose recommendations were never generated → Download PDF / Excel return quickly.
2. Print → the print dialog opens on the same page (no blank tab).
3. Overview shows the new charts; hover the bars; read the definitions panel.
4. Skill by skill shows Matched by and the similarity value; Recommendations shows the two charts.
