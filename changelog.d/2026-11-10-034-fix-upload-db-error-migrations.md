# Fix: "A database error occurred" on syllabus upload and list
- Type: fix
- Area: backend (database)
- Commit: see `git log --grep "Fix database error on syllabus upload"`

## What happened
Reproduced against the owner's running backend (faculty@ demo account, tiny test PDF):
`POST /api/syllabus/upload` and `GET /api/syllabus` both → 500 "A database error occurred."
Root cause: the backend was started from VS Code's Java runner, which kept an old classpath
**without Flyway** (added in 028). So migrations V1–V4 never ran; Hibernate
(`ddl-auto=update`) created the new tables/columns itself instead. Evidence: the demo college
got id 1 (V3 would have created "Default College" first). Hibernate cannot add the required
`syllabus.visibility` column (NOT NULL, no default) to a table with rows, so every syllabus
query failed. Existing accounts and syllabi were also left without a college.

## What changed
- `V3__institutions_departments_programs.sql` and `V4__syllabus_visibility.sql` rewritten to
  be safe on top of Hibernate-created objects (they had never been applied anywhere):
  tables/columns/indexes "if not exists", missing defaults set, foreign keys added only when
  missing (with the intended ON DELETE rules), Default College/General created only if
  missing, users without a college → Default College/General, syllabi without a college take
  their owner's, visibility filled (staff → DEPARTMENT, students → PRIVATE) then NOT NULL.
- `spring.jpa.hibernate.ddl-auto` default `update` → **`none`** (env `DDL_AUTO`): Flyway alone
  owns the schema, so this silent drift can't happen again.
- New `SchemaVersionCheck`: at startup logs a boxed ERROR "DATABASE NOT MIGRATED …" with the fix
  when `flyway_schema_history` is missing or below version 4; otherwise "Database schema at version 4".

## How to test
1. Stop the backend in VS Code. Start it from a terminal so pom.xml is used:
   `cd backend` → set env vars → `.\mvnw.cmd spring-boot:run`
   (or VS Code: Command Palette → "Java: Reload Projects", then run).
2. Log: Flyway "Successfully baselined schema with version: 1", then migrating to 2, 3, 4,
   then "Database schema at version 4". No "DATABASE NOT MIGRATED" box.
3. Upload a syllabus → works; Syllabi list loads.
4. Your own (non-demo) accounts now show "Default College · General" in the header.

## Risk / rollback
Each migration runs in a transaction; a failure stops startup with the SQL error and changes
nothing. Revert the commit to undo the code changes.
