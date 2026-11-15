# 052 — Fix backend start-up: "LlmProviders: No default constructor found"

## Cause
`LlmProviders` (change 050) has two constructors (one for Spring, one for tests) and neither was marked,
so Spring looked for a no-argument constructor and the backend failed to start. Unit tests don't start
Spring, and the full context test needs a database, so CI did not catch it.

## What changed
- `@Autowired` on `LlmProviders(Environment)`.
- New `BeanConstructorTest`: scans every Spring bean; fails if one has several constructors, none marked
  `@Autowired` and no no-arg constructor. Verified it fails without the fix. 74 backend tests pass.

## How to test
Restart the backend → it starts; log shows "Database schema at version 9".
