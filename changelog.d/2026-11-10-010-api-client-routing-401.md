# Shared API client, page URLs, and one place for expired sessions
- Type: feature (flow)
- Area: frontend, backend, infra
- Commit: see `git log --grep "Add page URLs"`

## Why
- Every page built its own axios call + token header; an expired login showed a
  different error box on each page and the user had to find Logout.
- No URLs: refresh always went back to the first page, back button did nothing.
- Backend answered "not logged in / expired token" with 403, the same code as
  "not allowed", so the frontend could not tell them apart.

## What changed
- New `frontend/src/apiClient.js`: one axios instance (base URL + token from
  sessionStorage). Any 401 fires `campuspulse:session-expired`. Also `errorMessage()` helper.
- All pages + `syllabusProcessing.js` use it (`axios.x(`${API_BASE_URL}/api…`)` →
  `api.x(`/api…`)`). Login/Register still use fetch (no token yet).
- `react-router` 7.9.4 added. URLs: `/upload`, `/curriculum`, `/jobs`, `/gap-analysis`,
  `/recommendations`, `/dashboard`; `/` and unknown paths go to the default page.
  Sidebar/step bar now change the URL (`App.jsx`, `main.jsx` wraps `BrowserRouter`).
- `App.jsx`: on session expiry → logout + login page shows "Your session expired. Sign in again."
- Backend `SecurityConfig`: unauthenticated requests now get **401** (`HttpStatusEntryPoint`);
  403 remains for "logged in but not allowed" (e.g. non-admin catalog import).
- Docker: `frontend/nginx.conf` with SPA fallback (page URLs work on refresh); Dockerfile copies it.

## How to test
1. Open http://localhost:5173/gap-analysis directly → Gap Analysis page opens.
2. Click Curriculum, then browser Back → returns to Gap Analysis. Refresh keeps the page.
3. Restart the backend with a new `JWT_SECRET` (old token becomes invalid), then click any
   page → you land on the login page with "Your session expired. Sign in again."
4. `curl -H "Authorization: Bearer x" localhost:8081/api/syllabus` → 401 (was 403).
Verified here in the browser: direct URL, back button, `/` redirect, expiry → login notice.

## Risk / rollback
New dependency (`react-router`) — run `npm install` after pulling. Revert the commit to undo.
