# Fix: backend did not compile after change 034
- Type: fix
- Area: backend
- Commit: see `git log --grep "Fix compile error in schema version check"`

## Why
`SchemaVersionCheck` (034) had `.\mvnw.cmd` inside a Java text block — `\m` is an illegal escape,
so the backend failed to compile. Claude's check after 034 reported success from a stale
incremental build. Lesson: verify with a full recompile (touch the changed file) and read the
"Compiling N source files" + BUILD lines.

## What changed
Message text now reads `mvnw.cmd spring-boot:run   (in the backend folder)`. Full recompile of
all 98 sources + all unit tests pass.

## How to test
`.\mvnw.cmd spring-boot:run` builds and starts.
