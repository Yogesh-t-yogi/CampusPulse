# Theme: approved palette as tokens, dark mode, Faculty/Student mode
- Type: feature (UI foundation)
- Area: frontend
- Commit: see `git log --grep "Add theme tokens"`

## Why
Colours were ~600 hard-coded hex values across App.css / FeatureAdditions.css, no dark
mode, leftover Vite starter CSS in index.css, and no way to tell Faculty from Student.
Palette approved by owner on 2026-11-10 (`_claude/plans/06-theme-palette.md`).

## What changed
- New `src/theme/tokens.css`: all colours as `--cp-*` variables (page, card, border, text,
  accent, matched/partial/gap/info, sidebar), light + `[data-theme="dark"]`, and
  `[data-mode="student"]` teal accent (indigo = faculty).
- New `src/theme/theme.js`: remembers theme + mode in localStorage; first visit follows the
  system light/dark setting. Applied in `main.jsx` before render.
- New `src/components/ThemeControls.jsx` + `src/theme/components.css`: Faculty/Student
  switch and light/dark button in the top bar; shared `.cp-button` styles.
- `App.css`, `FeatureAdditions.css`: ~620 colour values replaced by tokens with a mapping
  script (same look in light mode; status colours unchanged). Sidebar stays dark navy in
  both themes. Fonts → Inter (`--cp-font`).
- `index.css`: Vite starter styles removed; now imports tokens + minimal base.
- `index.html`: title "CampusPulse", Inter from Google Fonts.
- `App.jsx`: logout button uses `.cp-button` instead of inline red styles.
- Mode currently changes colours only; which features each mode shows comes in Phase 5.

## How to test
1. Open the app: light mode looks like before (indigo), font is Inter.
2. Top bar → moon icon: whole app turns dark, including upload drop zone and cards;
   sidebar stays dark; refresh keeps the choice.
3. Top bar → Student: accents turn teal (active menu item, buttons, step bar); Faculty
   turns them back to indigo; refresh keeps the choice.
4. Check Gap Analysis / Dashboard / Job Market in both themes for unreadable text and report
   anything that looks off (charts keep their fixed status colours).

## Risk / rollback
Purely visual. Some rarely-seen rules may still look slightly off in dark mode — report and
fix per page. Revert the commit to undo.
