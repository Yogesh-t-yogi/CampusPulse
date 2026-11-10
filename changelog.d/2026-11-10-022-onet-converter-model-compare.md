# O*NET to job-catalog converter; calibration can compare models
- Type: feature (data tooling)
- Area: ai-service, data, repo
- Commit: see `git log --grep "Add O.NET converter"`

## Why
Owner approved using O*NET (CC BY 4.0) and testing a second embedding model, and will
download the files themselves.

## What changed
- New `ai-service/tools/onet_to_csv.py`: reads `data/external/onet/Occupation Data.txt` and
  `Technology Skills.txt`, keeps computer (15-) and engineering (17-2) occupations, writes
  `data/jobs/onet-technology-roles.csv` in the importer format (up to 15 technologies,
  hot/in-demand first; `; , |` removed from names so the importer can't split them).
  `ONET_DIR` env var overrides the folder. Tested on a sample file.
- `tools/calibrate.py` takes an optional model name (2nd argument), e.g.
  `BAAI/bge-small-en-v1.5`.
- `.gitignore`: `data/external/` (raw downloads stay local).
- `data/README.md`: where to put files, steps, required attribution line.

## How to test
1. Place the two O*NET text files in `data/external/onet/`, run the converter → "Wrote N occupations".
2. Import the CSV as admin.
3. Run calibrate with `BAAI/bge-small-en-v1.5` and compare with MiniLM numbers.

## Risk / rollback
Tools only; app behaviour unchanged. Revert the commit to undo.
