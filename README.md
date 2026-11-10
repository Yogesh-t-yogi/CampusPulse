# CampusPulse

Skill-gap analysis between a college syllabus and industry jobs.

Upload a syllabus PDF. CampusPulse extracts its subjects, units and topics with AI, and compares
them with the skills a job asks for. You get a coverage report, recommendations, NBA CO/PO
coverage, and learning plans for students. One installation serves many colleges.

## What it does

| Area | Features |
|---|---|
| Syllabi | PDF upload (OCR for scanned files), AI extraction, curriculum editor, program + semester, sharing with the department, versions with a diff |
| Reports | Coverage overview, skill by skill, AI recommendations, CO/PO (NBA), compare several jobs, history over time, PDF and Excel export |
| Jobs | Admin-managed catalog (CSV import with de-duplication), paste your own job description, market insights |
| Students | Dashboard, profile and skills, target roles, readiness, learning plan with resources, resume match |
| Colleges | Many colleges, departments, programs; Excel import of students with a credentials sheet; department/college dashboard |
| Roles | Platform admin › College admin › HOD › Faculty › Student (see "Roles" below) |

## Stack

- **Frontend:** React 19 + Vite, React Router, Recharts, lucide icons (`frontend/`).
- **Backend:** Spring Boot 4 / Java 17, Spring Security (JWT), JPA + JdbcTemplate, Flyway, PDFBox + Tesseract, Apache POI (`backend/`).
- **AI service:** FastAPI + FastEmbed `all-MiniLM-L6-v2`, 384-dimensional embeddings (`ai-service/`). Runs locally; no token needed.
- **Database:** PostgreSQL with **pgvector**.
- **LLM:** Groq (`openai/gpt-oss-20b`) for extraction and recommendations.

```
frontend/      React app (pages/, ui/ kit, theme/ tokens)
backend/       Spring Boot API; db/migration holds Flyway V1..V8
ai-service/    Embedding service (+ tools/ for calibration, O*NET conversion)
data/          Starter job dataset, labelled evaluation pairs
changelog.d/   One fragment per change: what changed, how to test, risk
docker/        Docker helpers;  docker-compose.yml at the root
```

## Run it locally (Windows, one command)

### One-time setup
1. Install PostgreSQL with the [pgvector](https://github.com/pgvector/pgvector) extension, Java 17, Node 20+,
   Python 3.12, and (optional, for scanned PDFs) Tesseract OCR.
2. Create the database:
   ```bash
   psql -U postgres -c "CREATE DATABASE campuspulse;"
   ```
   Flyway creates every table and enables `vector` on first start.
3. Create your settings file and fill it in:
   ```bash
   copy .env.example .env
   ```
   | Setting | What to put |
   |---|---|
   | `DB_PASSWORD` | Your PostgreSQL password |
   | `JWT_SECRET` | Any random text of 32+ characters (keep it stable) |
   | `GROQ_API_KEY` | Your key from console.groq.com |
   | `ADMIN_EMAILS` | Emails that become platform admins (optional) |
   | `DEMO_USERS_PASSWORD` | Set it to create the demo college and demo accounts (optional, local only) |
   | `CORS_ALLOWED_ORIGINS` | Only if the frontend runs on another address (default `http://localhost:5173`) |
   | `RATE_LIMIT_AUTH`, `RATE_LIMIT_AI` | Requests per minute per IP for sign-in and AI calls (defaults 10 / 30; 0 = off) |

### Every time
```bash
.\start-dev.cmd
```
It creates the Python environment and installs packages on the first run. Then it opens three
windows: the AI service on port 8000, the backend on port 8081 and the frontend on port 5173.
When the backend is ready, it opens http://localhost:5173. In the Backend window, check for
**"Database schema at version 8"**. Close the three windows to stop.

After pulling changes that touch `backend/pom.xml` or `db/migration`, restart the backend the
same way (or `.\mvnw spring-boot:run` in `backend/`). Some IDE runners skip Flyway.

### Run the parts by hand (any OS)
```bash
cd ai-service && python -m venv venv && venv/bin/pip install -r requirements.txt && venv/bin/python -m uvicorn app:app --port 8000
```
```bash
cd backend && ./mvnw spring-boot:run
```
```bash
cd frontend && npm install && npm run dev
```
The backend reads the same variables as `.env` from the environment.

### Docker (optional)
```bash
docker compose up --build
```
This uses `.env`. Open http://localhost:5173. Data is kept in the `db-data` volume; `docker compose down -v` deletes it.

## Roles

| Role | Can |
|---|---|
| Platform admin | Everything, across colleges (switch college in the header) |
| College admin | Their college: departments, programs, users, job catalog, all syllabi, dashboards |
| HOD | Their department: users, syllabi, outcomes, department dashboard |
| Faculty | Own syllabi + department-shared ones, reports, CO/PO, department dashboard |
| Student | Their program's shared syllabi, own profile, target roles, learning plan, resume match |

Accounts are usually created by an admin (Users → Import from Excel → download the credentials
sheet). New users must change their password at first sign-in. There is no email sending.

## Development

```bash
cd backend && ./mvnw test -Dtest='!CampuspulseBackendApplicationTests' -Dsurefire.failIfNoSpecifiedTests=false
```
```bash
cd frontend && npx eslint . && npm run build
```
CI (`.github/workflows/ci.yml`) runs the backend tests, frontend lint + build, and an AI service import check.

Every change adds a fragment in `changelog.d/` (what changed, how to test, risk). New database
changes go in a new `V<n>__*.sql` migration; bump `SchemaVersionCheck.EXPECTED_VERSION`.

Matching thresholds were calibrated on `data/eval/skill-topic-pairs.csv` (see `ai-service/tools/calibrate.py`).

## Troubleshooting

| Problem | Fix |
|---|---|
| Red "DATABASE NOT MIGRATED" box or "A database error occurred" | Restart the backend with `start-dev.cmd` / `mvnw` so Flyway runs |
| "Can't reach CampusPulse" on sign-in | The backend window isn't running or failed — read its log |
| Topics "could not be indexed" | The AI service (port 8000) isn't running |
| Recommendations or extraction fail | Check `GROQ_API_KEY`; Groq free tier has rate limits |
| "Too many requests" | Rate limit hit; wait a minute (raise `RATE_LIMIT_*` in `.env` if needed) |
| Blank page after updating | Stop the frontend, delete `frontend/node_modules/.vite`, start again |
| Port already in use | Close the old window or process using 8000 / 8081 / 5173 |

## Security notes

- Never commit `.env`, passwords or API keys; `.env.example` holds placeholders only.
- Use a long, stable `JWT_SECRET`; for anything beyond a local machine, serve over HTTPS and
  set `CORS_ALLOWED_ORIGINS` to the real frontend address.
- Demo accounts exist only when `DEMO_USERS_PASSWORD` is set — leave it empty outside local demos.

## License

See [LICENSE](LICENSE).
