# Versioned database migrations with Flyway (Phase 6.1)
- Type: infra
- Area: backend (database)
- Commit: see `git log --grep "Add Flyway"`

## Why
Phase 6 (multi-college) changes the database structure a lot. Hibernate `ddl-auto=update`
plus hand-written startup classes cannot rename, move data or be reviewed; numbered SQL
scripts can.

## What changed
- `pom.xml`: `spring-boot-starter-flyway`, `flyway-database-postgresql`.
- `src/main/resources/db/migration/`:
  - `V1__baseline.sql` — the full current schema (all 10 tables, pgvector). Runs only on an
    **empty** database.
  - `V2__catch_up_existing_databases.sql` — idempotent catch-up for databases created before
    Flyway: unit.name → TEXT, processing columns, skill_embedding, analysis_result
    (+ algo_version), USER → FACULTY.
- `application.properties`: `spring.flyway.baseline-on-migrate=true`,
  `baseline-version=1` → an existing database is recorded as V1 (V1 skipped), then V2 runs.
  `ddl-auto` stays `update` as a safety net (env `DDL_AUTO`); switch to `validate` once
  Flyway is proven on real data.
- Removed startup classes `SchemaSetup` and `UnitNameSchemaMigration` (now in V2).

## How to test
Not run against a database here (no DB access). On first backend start:
1. Log shows Flyway: "Creating Schema History table … Successfully baselined schema with
   version: 1" then "Migrating schema … to version 2" and "Successfully applied 1 migration".
2. `SELECT version, description, success FROM flyway_schema_history;` → rows 1 (baseline)
   and 2 (catch up), success = t.
3. App works as before (upload, report, compare).
4. Fresh database (e.g. `docker compose down -v` then up): V1 and V2 both applied.

## Risk / rollback
If V2 fails, the backend stops at startup with the SQL error — send it to Claude. Rollback:
revert the commit and `DROP TABLE flyway_schema_history;` (V2 statements are additive).
