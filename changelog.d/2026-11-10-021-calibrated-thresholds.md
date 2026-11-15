# Calibrated matching thresholds from a labelled test set (0.70/0.50 → 0.50/0.25)
- Type: fix (AI quality)
- Area: data, ai-service, backend
- Commit: see `git log --grep "Calibrate matching thresholds"`

## Why
The MATCHED/PARTIAL cut-offs (0.70/0.50) were guesses. Measured on labelled data they were
far too strict for all-MiniLM-L6-v2: most real matches were reported as gaps.

## What changed
- New `data/eval/skill-topic-pairs.csv`: 113 hand-labelled pairs (job skill × typical Indian
  CS/IT syllabus topic → MATCHED / PARTIAL / GAP).
- New `ai-service/tools/calibrate.py`: embeds pairs exactly like the backend (skill text,
  topic "name description", keyword boost) and grid-searches cut-offs; also compares a
  "knowledge of {skill}" wording.
- Results (113 pairs):
  | Cut-offs | Accuracy | False gaps | False matches |
  |---|---|---|---|
  | 0.70 / 0.50 (old) | 47% | 41 | 0 |
  | 0.55 / 0.35 | 69% | 22 | 1 |
  | 0.50 / 0.30 | 75% | 16 | 1 |
  | **0.50 / 0.25 (new default)** | **78%** | **12** | **2** |
  | 0.48 / 0.20 (best on this set) | 83% | 6 | 3 |
  "knowledge of {skill}" wording: no gain → skills stay plain.
- Defaults now `matching.matched-threshold=0.50`, `matching.partial-threshold=0.25`
  (env `MATCHING_MATCHED_THRESHOLD`, `MATCHING_PARTIAL_THRESHOLD`). 0.50/0.25 chosen over the
  set's best because real reports take the best of many topics, which raises scores slightly.
- `AnalysisResultService.ALGO_VERSION` 2 → 3: saved reports recompute on next open.

## How to test
1. `cd ai-service; .\venv\Scripts\python.exe tools\calibrate.py ..\data\eval\skill-topic-pairs.csv`
   → prints the table above.
2. Restart backend, open a report → coverage is higher than before; skills that are clearly
   taught show MATCHED/PARTIAL instead of GAP.
3. Spot-check that unrelated skills (e.g. Docker vs a syllabus without containers) stay GAP.

## Risk / rollback
Labels were written by Claude, not faculty — the set should be extended with the owner's
syllabi. To go back: set `MATCHING_MATCHED_THRESHOLD=0.70`, `MATCHING_PARTIAL_THRESHOLD=0.50`.
