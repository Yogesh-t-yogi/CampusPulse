# Student dashboard, profile, learning plan; Resume match and Market insights reworked
- Type: feature (UI)
- Area: frontend
- Commit: see `git log --grep "Add student pages"`

## What changed
- **Home for students** (`pages/StudentHome.jsx`): readiness % per target role (bar + every skill
  coloured: you have it / taught in your course / partly taught / to learn), stats (target roles,
  best readiness, your skills, learning progress), "Learn next" (top 5), course, shortcuts.
- **My profile** (`pages/StudentProfile.jsx`, `/profile`): choose up to 5 target roles (search the
  catalog), my skills (add, remove, source: resume / added by you / learned), my course syllabus
  (automatic or chosen), program and department.
- **Learning plan** (`pages/LearningPlan.jsx`, `/learning`): per skill — why (roles needing it,
  catalog demand, course status), where (official docs + freeCodeCamp, YouTube, NPTEL/SWAYAM,
  GeeksforGeeks, Coursera free), how (4-step AI roadmap + project, loaded on demand and cached),
  progress (Not started / Learning / Done; Done adds the skill); filter; overall progress bar.
- **Resume match** (`pages/ResumeMatch.jsx`, replaces the old Job Market section): consent,
  extracted skills, **Save these skills to my profile**, matches with target roles first,
  matched/missing chips, **Add as target role**, **Learn missing skills**.
- **Market insights** (`pages/MarketInsights.jsx`, replaces the old section): location and keyword
  filters, stats, most requested skills chart, most common roles, and **Your gaps vs the market**
  (students) / **Curriculum vs the market** (staff, pick a syllabus) with status + matched topic.
- Sidebar: new **My growth** group for students (My profile, Learning plan, Resume match); Tools
  keeps Market insights. `src/studentStatus.js` holds the status labels/colours.

## How to test (backend 041, restart → Flyway V7)
1. student@ → Home: "Choose the roles you want" → My profile → pick 2–3 roles → Save roles.
2. Home shows readiness per role; Learn next lists skills.
3. Learning plan → open "How to learn it" (first time ~5 s, then instant); set Learning / Done →
   Done adds the skill; Home readiness goes up.
4. Resume match → upload PDF with consent → Save skills → target roles listed first.
5. Market insights: filter Bengaluru / "data"; student sees have/taught/to learn; faculty@ picks a
   syllabus and sees taught/partly/not taught.

## Risk / rollback
UI only on top of 041. Revert to undo.
