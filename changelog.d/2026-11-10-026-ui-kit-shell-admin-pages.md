# UI upgrade part 1: UI kit, new app frame, Home, Syllabi, Users, Job catalog
- Type: feature (UI)
- Area: frontend
- Commit: see `git log --grep "UI upgrade part 1"`

## Why
Owner asked for a complete UI upgrade before Phase 6: consistent sidebar, header, icons,
page skeletons and full index / create / edit / delete screens.

## What changed
UI kit — `src/ui/index.jsx` + `src/ui/ui.css` (theme tokens, light + dark):
PageHeader, Page, Card, StatCard, Button, IconButton, Badge (status/role colours),
EmptyState, Alert, Skeleton, SearchInput, Field, DataTable (toolbar, empty, loading),
Modal, Drawer, ConfirmDialog, ToastProvider + `useToast` (`src/ui/toast.js`).
App frame — `components/Sidebar.jsx`, new `components/Topbar.jsx`, `components/shell.css`:
- Sidebar groups: Overview (Home, Syllabi) · Analysis (Upload syllabus, Review curriculum,
  Choose job, Report — locked until earlier steps done, lock icon) · Tools (Market insights,
  Resume match) · Admin (Job catalog, Users). Items filtered by role. AI status at the bottom.
- Header: breadcrumb title, syllabus + job pickers (moved out of the old grey bar), theme
  toggle, user menu (avatar initials, name, role, Sign out). Empty slot reserved for the
  Phase 6 college / department switcher. Sticky; stacks on small screens.
- Step bar shows only on Upload / Curriculum / Choose job / Report. Notices use Alert.
New pages
- **Home** (`/home`, new default after sign-in): greeting, 4 stats (syllabi, topics indexed,
  job roles, current coverage), recent syllabi, "Getting started" checklist with next step;
  students also get a Resume match shortcut.
- **Syllabi** (`/syllabi`): table with search + status filter; Open, Use for analysis,
  Rename (dialog), Re-process, Delete (confirm).
- **Users** (admin, rewritten): search + role filter + role counts; Add user drawer →
  one-time password dialog with Copy; inline role change; Reset password (confirm → new
  one-time password); Delete (confirm).
- **Job catalog** (admin, `/catalog`, new page replacing the old catalog section): search +
  type filter; Add / Edit job drawer (title, company, location, description, skills);
  Delete (confirm); Import CSV dialog.
Removed `components/ThemeControls.jsx` (unused since change 016).

## How to test (restart frontend; backend from change 025 needed)
1. Sign in → lands on Home; stats and checklist match your data.
2. Sidebar: Analysis items show a lock until a syllabus/job is chosen.
3. Header: change syllabus/job from the pickers; toggle theme; user menu → Sign out.
4. Syllabi: rename one, re-process one (status changes to Processing), delete one (confirm).
5. Admin → Users: add a user, copy the one-time password, sign in with it in another
   browser; change role; reset password; delete.
6. Admin → Job catalog: add a job with skills, edit it, delete it, import the starter CSV.
7. Check everything in dark mode and at a narrow window width.

## Risk / rollback
Pages not yet moved to the new layout (Upload, Curriculum, Choose job, Report) keep their
old headers until part 2. Revert the commit to undo.
