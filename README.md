# CampusPulse

Skill-gap analysis between a college syllabus and industry jobs.

Upload a syllabus PDF. CampusPulse extracts its subjects, units and topics with AI, and compares
them with the skills a job asks for. You get a coverage report, recommendations, NBA CO/PO
coverage, and learning plans for students. One installation serves many colleges.

## What it does

| Area | Features |
|---|---|
| Syllabi | PDF upload (scanned pages read by LlamaParse, no OCR install), AI extraction, curriculum editor, program + semester, sharing with the department, versions with a diff |
| Reports | Coverage overview, skill by skill, AI recommendations, CO/PO (NBA), compare several jobs, history over time, PDF and Excel export, print |
| Jobs | Built-in catalog of 20 roles, admin CSV import (35-role dataset in `data/jobs/`, de-duplicated), paste your own job description, market insights |
| Students | Dashboard, profile and skills, target roles, readiness, learning plan with resources, resume match |
| Colleges | Many colleges, departments, programs; Excel import of students with a credentials sheet; department/college dashboard |
| Roles | Platform admin › College admin › HOD › Faculty › Student (see "Roles" below) |

## Stack

- **Frontend:** React 19 + Vite, React Router, Recharts, lucide icons (`frontend/`).
- **Backend:** Spring Boot 4 / Java 17, Spring Security (JWT), JPA + JdbcTemplate, Flyway, PDFBox, Apache POI (`backend/`).
- **AI service:** FastAPI + FastEmbed `all-MiniLM-L6-v2`, 384-dimensional embeddings (`ai-service/`). Runs locally; no token needed.
- **Database:** PostgreSQL with **pgvector**.
- **Chat AI (with fallback):** Gemini (`gemini-3.5-flash`) → Groq (`openai/gpt-oss-20b`), OpenAI-compatible APIs,
  for topic extraction, recommendations and learning plans.
- **Scanned PDFs:** LlamaParse (LlamaCloud) → Gemini / Groq vision models → Tesseract only if installed.

```
frontend/      React app (pages/, ui/ kit, theme/ tokens)
backend/       Spring Boot API; db/migration holds Flyway V1..V9
ai-service/    Embedding service (+ tools/ for calibration, O*NET conversion)
data/          Starter job dataset, labelled evaluation pairs
changelog.d/   One fragment per change: what changed, how to test, risk
docker/        Docker helpers;  docker-compose.yml at the root
```

## Run it locally (Windows, one command)

### One-time setup
1. Install PostgreSQL with the [pgvector](https://github.com/pgvector/pgvector) extension, Java 17, Node 20+,
   Python 3.12. No OCR program is needed: scanned PDFs are read through LlamaParse / AI.
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
   | `GEMINI_API_KEY` and/or `GROQ_API_KEY` | At least one, for AI extraction and recommendations ([aistudio.google.com/apikey](https://aistudio.google.com/apikey), [console.groq.com/keys](https://console.groq.com/keys)) |
   | `LLAMA_CLOUD_API_KEY` | LlamaParse key (`llx-…`, [cloud.llamaindex.ai](https://cloud.llamaindex.ai)) for scanned PDFs (optional; without it Gemini/Groq read them) |
   | `ADMIN_EMAILS` | Emails that become platform admins (optional) |
   | `SELF_REGISTRATION` | `false` (default): HOD / college admin create accounts with the department set; only `ADMIN_EMAILS` can use the sign-up page. `true`: anyone can sign up |
   | `DEFAULT_USER_PASSWORD` | First password for new accounts (8+ characters, must be changed at first sign-in). Empty: random one-time passwords. Also creates the `ADMIN_EMAILS` platform admin at first start |
   | `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `APP_URL` | Optional SMTP for welcome and password-reset emails; empty `MAIL_HOST` = no emails |
   | `LOG_FILE`, `LOG_MAX_FILE_SIZE`, `LOG_MAX_DAYS` | Rotated log file (default `logs/campuspulse.log`, 10 MB per file, 14 days) |
   | `DEMO_USERS_PASSWORD` | Set it to create the demo college and demo accounts (optional, local only) |
   | `CORS_ALLOWED_ORIGINS` | Only if the frontend runs on another address (default `http://localhost:5173`) |
   | `RATE_LIMIT_AUTH`, `RATE_LIMIT_AI` | Requests per minute per IP for sign-in and AI calls (defaults 10 / 30; 0 = off) |
   | `PDF_AI_MODE` | `auto` (read only scanned pages, default), `always` (every page), `off` |

   Model names, the provider order (`LLM_ORDER`) and the LlamaParse tier can also be changed; see `.env.example`.

### Every time
```bash
.\start-dev.cmd
```
It creates the Python environment and installs packages on the first run. Then it opens three
windows: the AI service on port 8000, the backend on port 8081 and the frontend on port 5173.
When the backend is ready, it opens http://localhost:5173. In the Backend window, check for
**"Database schema at version 9"**. Close the three windows to stop.

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

## How a syllabus PDF is read

1. Each page's text layer is read directly (free, instant).
2. Pages with no real text (scans, photos, garbled text) are sent to **LlamaParse** in one job.
3. Pages it can't read go to a vision model (**Gemini**, then **Groq**), then Tesseract if installed.
4. Results are cached per file, so re-processing the same PDF never calls the AI again.
5. The upload screen shows progress and a summary, e.g. "12 pages: 9 with text, 3 read by LlamaParse".
   Pages that couldn't be read stay queued; **Re-process** retries them.

Signed-in users can see which AI providers are set up at `http://localhost:8081/api/health/ai-providers`
(no keys shown).

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
| Recommendations or extraction fail | Set `GEMINI_API_KEY` and/or `GROQ_API_KEY`; the Backend log names the provider and its error (free tiers have rate limits) |
| "No page of this PDF could be read" | Check `LLAMA_CLOUD_API_KEY` (US region; EU keys need `LLAMA_CLOUD_BASE_URL`) or a Gemini key, then **Re-process** |
| Log: "model or URL not found (HTTP 404)" | Providers retire models; set a current name (e.g. `GEMINI_MODEL`) in `.env` |
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

## Deploying

- Uptime: `GET /api/health` returns 200 `UP` when the API and database answer, 503 `DOWN` otherwise
  (use UptimeRobot, Better Stack or a cron `curl`).
- Logs: rotated files in `logs/` (Docker: `./logs` on the host).
- HTTPS and upload size: see `deploy/nginx-site.conf` (`client_max_body_size 12m` for 10 MB syllabus PDFs).
- Email: fill the `MAIL_*` settings, then My account > **Send test email** (college / platform admin).
- `docker-compose.yml` keeps the database and the embedding service on `127.0.0.1` only.
