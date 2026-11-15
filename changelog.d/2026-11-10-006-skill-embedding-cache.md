# Cache job-skill embeddings in the database
- Type: feature (performance)
- Area: backend
- Commit: see `git log --grep "Cache skill embeddings"`

## Why
Every gap report embedded every job skill again with one HTTP call each (and the
recommendations page did it a second time). Skills like "SQL" or "Git" repeat everywhere.

## What changed
- New `config/SchemaSetup.java`: creates table `skill_embedding(skill_key PK, embedding
  vector(384), model, created_at)` with `CREATE TABLE IF NOT EXISTS` at startup.
- New `service/embedding/SkillEmbeddingCache.java`: looks up vectors by lower-cased skill;
  computes only missing ones via `/embed/batch` and stores them.
- `SemanticMatchingService.matchSkills`: gets all skill vectors in one go from the cache,
  then runs the same pgvector search as before. Matching rules/thresholds unchanged.

## How to test
1. Open Gap Analysis for a syllabus + job: same numbers as before this change.
2. Open it again / switch to another job sharing skills: the AI service console shows
   no `/embed` calls for skills already seen.
3. `SELECT count(*) FROM skill_embedding;` grows as new skills are analysed.

## Risk / rollback
Results identical (MiniLM lower-cases input, so the lower-cased key gives the same vector).
Revert the commit; the table can be dropped with `DROP TABLE skill_embedding;`.
