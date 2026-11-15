# Hybrid skill matching: explicit mentions are never reported as gaps
- Type: feature (AI quality)
- Area: backend, frontend
- Commit: see `git log --grep "Add hybrid keyword"`

## Why
Matching used only embedding similarity between the bare skill name and topic text.
Short skill names ("Java") vs long topic text often scored below 0.70, so a skill that
is literally a topic title could be PARTIAL or GAP. Thresholds were hard-coded.

## What changed
- New `service/matching/KeywordTopicMatcher.java`: finds the (alias-normalised) skill as a
  whole word/phrase in topic names, else in descriptions. Keeps `C++`, `C#`, `Node.js`;
  "Java" does not match "JavaScript"; skills shorter than 3 letters (C, R, Go) only count
  when they are the entire topic name.
- `SemanticMatchingService.matchOne`: semantic best topic decides the status, then a
  keyword hit can raise it — named in a topic title → MATCHED, only in a description →
  at least PARTIAL. Never lowers a status.
- `SemanticSkillMatch.matchedBy`: `SEMANTIC`, `KEYWORD` or null.
- Thresholds configurable: `matching.matched-threshold` (0.70, env
  `MATCHING_MATCHED_THRESHOLD`), `matching.partial-threshold` (0.50, env
  `MATCHING_PARTIAL_THRESHOLD`).
- Saved results: new column `analysis_result.algo_version` (current = 2). Older saved
  reports are ignored and recomputed automatically on next open.
- Report → Skill by skill: under the topic, "Skill is named in this topic" or
  "Closest topic by meaning" (replaces "Topic ID: n").
- Test: `HybridMatchingTest` (5 cases).

## How to test
1. Open a report saved before this change → it recomputes (coverage may go up).
2. A skill whose name appears in a topic title shows MATCHED with "Skill is named in this topic".
3. A job skill like "JavaScript" with only a "Java" topic is not keyword-matched.

## Risk / rollback
Coverage numbers change (usually up). Revert the commit to undo; then use Re-run analysis.
