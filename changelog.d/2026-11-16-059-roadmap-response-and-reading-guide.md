# 059 — Fix "How to learn it" (no steps) and redesign the report's reading guide

## Cause of "The AI didn't return learning steps"
`GET /api/student/roadmap` returned a Jackson 2 `JsonNode`. Spring Boot 4 writes HTTP responses with
Jackson 3, which serialised that node as a bean description
(`{"array":false,"bigDecimal":false,...,"nodeType":"OBJECT"...}`) instead of its content, so the page never
received `steps` even when the AI answered correctly.

## What changed
- `StudentController.roadmap` returns `Map<String, Object>` via new `StudentService.roadmapView()`
  (the normalised roadmap converted to plain maps and lists).
- `ResponseTypeTest`: no `@RestController` method may return a `com.fasterxml.jackson` type; the roadmap
  serialised with Jackson 3 contains its `steps` and `summary`.
- Report Overview "How to read this report" redesigned: colour-coded similarity scale (Gap 0–0.25, Partly
  covered 0.25–0.50, Covered 0.50–1.0) with tick labels, a highlighted coverage formula, and icon tiles with a
  coloured edge for Covered, Partly covered, Gap, Similarity, Matched by and Closest topic.
- 143 backend tests pass; lint and build pass.

## How to test
1. Restart the backend. Student → Learning plan → "How to learn it" shows the steps and project.
2. Report → Overview → scroll to "How to read this report" (light and dark theme, narrow window).
