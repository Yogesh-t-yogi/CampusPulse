# changelog.d — change fragments

One file per change. Never edit an old fragment; add a new one.

## File name
`YYYY-MM-DD-NNN-short-slug.md`  (NNN = running number for that day)

## Template
```markdown
# <Title>
- Type: security | fix | feature | infra | docs | chore
- Area: backend | frontend | ai-service | infra | repo
- Commit: <hash or "pending">

## Why
## What changed
(files and behaviour, before → after)
## How to test
## Risk / rollback
```
