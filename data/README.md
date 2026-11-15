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

## Using O*NET (CC BY 4.0)
1. Download the O*NET database "Text" files from https://www.onetcenter.org/database.html.
2. Copy `Occupation Data.txt` and `Technology Skills.txt` into `data/external/onet/`
   (folder is git-ignored).
3. `cd ai-service` → `.\venv\Scripts\python.exe tools\onet_to_csv.py`
   → writes `data/jobs/onet-technology-roles.csv` (computer + engineering occupations,
   up to 15 technologies each, hot/in-demand first).
4. Import it as admin (Job catalog → Import CSV).
Attribution: "This product includes information from O*NET (www.onetcenter.org) by the
U.S. Department of Labor, Employment and Training Administration, used under the CC BY 4.0 license."

## Comparing embedding models
`.\venv\Scripts\python.exe tools\calibrate.py ..\data\eval\skill-topic-pairs.csv BAAI/bge-small-en-v1.5`
(FastEmbed downloads the model once into its cache; nothing to place by hand.)
