# Starter India tech-roles job dataset (owned, importable)
- Type: feature (data)
- Area: data, backend tests
- Commit: see `git log --grep "Add starter India"`

## Why
Owner has syllabi but no job data. The built-in catalog had 8 jobs. Public India job-board
datasets found online (Kaggle scrapes of Naukri/LinkedIn) state no licence; O*NET (CC BY 4.0)
and ESCO (EU open data) are open but need owner approval to download and adapt.

## What changed
- New `data/jobs/india-tech-roles-starter.csv`: 35 roles (backend, full stack, mobile, data,
  ML/GenAI, DevOps/cloud/SRE, security, networking, QA, embedded/IoT, .NET, PHP, UI/UX,
  Salesforce, SAP, blockchain, games, support, product analytics) across Indian cities,
  5–10 skills each, written from scratch.
- New `data/README.md`: origin, how to import, licence notes for larger sources.
- Test `StarterDatasetImportTest`: imports the file through the real CSV importer; every
  row imports, each role has several skills.

## How to test
Admin → Tools → Job catalog → Import CSV → `data/jobs/india-tech-roles-starter.csv`
→ "35 jobs imported"; Market insights chart shows more skills; Choose job lists the roles.

## Risk / rollback
Importing twice creates duplicates (importer does not de-duplicate). Delete the file /
revert the commit to undo.
