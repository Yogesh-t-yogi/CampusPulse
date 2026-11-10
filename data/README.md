# Data

## jobs/india-tech-roles-starter.csv
35 common Indian tech roles (title, city, short description, 5–10 required skills).
Written from scratch for CampusPulse (not copied from job boards), so the project owns it.
It is a starter catalog for demos and testing, not live market data.

Import: log in as admin → Tools → Job catalog → Import CSV → choose this file.
Columns: `title, company, location, description, skills` (skills separated by `;`).
`StarterDatasetImportTest` checks the file stays importable.

## Larger datasets (not included — check licence first)
| Source | Licence | Notes |
|---|---|---|
| O*NET Technology Skills (US DOL) | CC BY 4.0 (attribution required) | Occupation → tools/technologies; US occupations |
| ESCO (European Commission) | EU open data, reuse allowed; a few items have third-party conditions | Occupations + skills, many languages |
| Kaggle India job-board scrapes (Naukri, LinkedIn) | Not stated on listing pages | Check licence and source site terms before use |

## eval/
Labelled skill ↔ topic pairs used to calibrate matching thresholds
(see `ai-service/tools/calibrate.py`).
