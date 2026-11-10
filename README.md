
# CampusPulse — AI-Driven Curriculum-to-Industry Gap Analyzer

CampusPulse is a web application that analyzes academic curricula against industry job requirements. It uses semantic embeddings and AI-assisted recommendations to identify matched skills, partial matches, and curriculum gaps.

## Technology Stack

| Component | Technologies |
|---|---|
| Frontend | React, Vite, Axios, Recharts |
| Backend | Java 17, Spring Boot, Maven Wrapper, Spring Security, JWT |
| Database | PostgreSQL, pgvector |
| AI embedding service | Python, FastAPI, FastEmbed (local ONNX inference) |
| LLM integration | Groq API |

## Project Structure

```text
CampusPulse/
├── backend/       # Spring Boot REST API
├── frontend/      # React application
├── ai-service/    # Python embedding service
├── README.md
└── .gitignore
```

## Prerequisites

Install the following on Windows:

- Git
- Java JDK 17
- Node.js and npm
- Python 3.12 (64-bit recommended)
- PostgreSQL 18
- pgvector compatible with PostgreSQL 18
- A Groq API key

The first build may take several minutes while dependencies are installed.

## Free job data strategy

The app no longer depends on Adzuna. The default job source is now a free local dataset and starter catalog that works without keys, rate limits, or external configuration.

Use a CSV import when you want a larger India-focused job dataset. A starter catalog is seeded automatically on first startup so the job market still works immediately in development.

### Available free job and learning features

- Skill aliases (for example, JS = JavaScript and Postgres = PostgreSQL) are
  normalized during dataset import and matching.
- AI Recommendations includes a step-by-step learning roadmap for missing or
  partially covered skills, with freeCodeCamp and available official-document
  links. Roadmap generation uses the configured Groq API key.
- The Job Market page accepts a student resume PDF and compares its extracted
  skills against accessible job profiles. The resume is not saved by
  CampusPulse; resume text is sent to the configured Groq API for skill
  extraction only after the user confirms this in the UI.
- Gap Analysis can be printed or saved as PDF from the browser using
  **Export report as PDF**.
- The Job Market page charts the most requested skills in the local catalog
  using Recharts.
- Import a CSV dataset from Job Market. Provide a `title`, `job_title`, or
  `role` column; optional columns include `company`, `location`,
  `description`, and `skills` (or `required_skills`). Skills can be separated
  by commas, semicolons, or pipes. CSV uploads are limited to 10 MB and
  10,000 rows. Verify a dataset's license and permitted use before importing.

## 1. Clone the Repository

Open PowerShell:

```powershell
cd "$env:USERPROFILE\Desktop"
git clone https://github.com/Yogesh-t-yogi/CampusPulse.git
cd CampusPulse
```

Verify the project folders:

```powershell
Get-ChildItem
```

## 2. Configure PostgreSQL

Start PostgreSQL and connect using SQL Shell (psql) or the PostgreSQL command-line client.

Create the database:

```sql
CREATE DATABASE campuspulse;
```

Connect to the database:

```sql
\c campuspulse
```

Enable pgvector:

```sql
CREATE EXTENSION vector;
```

Verify the extension:

```sql
SELECT extversion FROM pg_extension WHERE extname = 'vector';
```

If the database already exists, do not create it again. Connect to it and verify that the vector extension is enabled.

**Important:** A Git clone does not copy the database records from another computer. You may need to upload your syllabus and recreate job-market demo data.

## 3. Configure the Python AI Service

Open a separate PowerShell terminal:

```powershell
cd "$env:USERPROFILE\Desktop\CampusPulse\ai-service"

py -3.12 -m venv venv
.\venv\Scripts\Activate.ps1

python -m pip install --upgrade pip
pip install -r requirements.txt
```

If PowerShell blocks virtual-environment activation, run:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\venv\Scripts\Activate.ps1
```

Start the service:

```powershell
python -m uvicorn app:app --host 127.0.0.1 --port 8000
```

Keep this terminal open.

Verify the service in another terminal or browser:

http://127.0.0.1:8000/health

The embedding service runs `sentence-transformers/all-MiniLM-L6-v2` locally through FastEmbed and returns 384-dimensional embeddings. It does not require a Hugging Face token or inference credits. On first startup, FastEmbed downloads the public model files; subsequent runs use the local cache.

## 4. Configure and Start Spring Boot

Open another PowerShell terminal:

```powershell
cd "$env:USERPROFILE\Desktop\CampusPulse\backend"
```

Set the required environment variables in this same terminal.

```powershell
$env:DB_PASSWORD = "YOUR_POSTGRES_PASSWORD"
$env:GROQ_API_KEY = "YOUR_GROQ_API_KEY"
```

Generate a secure JWT secret for this local run:

```powershell
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$bytes = New-Object byte[] 32
$rng.GetBytes($bytes)
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
$rng.Dispose()
```

Replace the database password and Groq API key placeholders with your own values. Never commit actual credentials to GitHub. Syllabus processing and custom job skill extraction use the Groq API.

Start the backend:

```powershell
.\mvnw.cmd spring-boot:run
```

Wait until Spring Boot starts successfully on port `8081`.

Verify the health endpoint:

http://localhost:8081/api/health

**JWT note:** The generated JWT secret is temporary. If you restart the backend with a different secret, existing login tokens will no longer be valid. Generate the secret again when starting a new development session and log in again.

## 5. Start the React Frontend

Open another PowerShell terminal:

```powershell
cd "$env:USERPROFILE\Desktop\CampusPulse\frontend"

npm install
npm run dev
```

Open the URL displayed by Vite, normally:

http://localhost:5173

Use the registration page to create a user account, then log in.

During Vite development, the frontend uses `http://localhost:8081` by
default. To use another backend, set `VITE_API_BASE_URL` in the same terminal
before starting Vite:

```powershell
$env:VITE_API_BASE_URL = "http://localhost:8081"
npm run dev
```

The production frontend defaults to the deployed CampusPulse backend.

## 6. Start-Up Order

Start the components in this order:

1. PostgreSQL database.
2. Python AI embedding service on port `8000`.
3. Spring Boot backend on port `8081`.
4. React frontend on port `5173`.

Keep the Python, backend, and frontend terminals open while using the application.

## 7. Main Features

- **Guided analysis flow:** Upload and process a syllabus, select an existing job profile or paste a job description, review the curriculum, analyze skill gaps, and view recommendations and dashboard metrics.
- **Authentication:** User registration, login, and JWT-protected API access.
- **Dashboard:** Overview of curriculum-to-industry matching results.
- **Curriculum:** View syllabus subjects, units, and topics.
- **Job Market:** Choose an existing job posting or create a private profile from a pasted description; AI extracts skills from the description.
- **Gap Analysis:** Identify matched, partially matched, and missing skills using semantic similarity.
- **AI Recommendations:** Generate curriculum and learning recommendations.

## 8. Troubleshooting

### Database connection failure

- Confirm PostgreSQL is running.
- Check the database name, port, username, and password.
- Ensure `DB_PASSWORD` is set in the terminal running Spring Boot.

### JWT or authentication errors

- Confirm `JWT_SECRET` is set in the backend terminal.
- Log in again after restarting the backend with a new JWT secret.
- Check that the frontend sends the access token in the `Authorization: Bearer <token>` header.

### AI service unavailable

- Activate the Python virtual environment.
- Install `requirements.txt`.
- Start Uvicorn on port `8000`.
- Test `http://127.0.0.1:8000/health`.

### Groq API errors

- Verify `GROQ_API_KEY`.
- Check your Groq account's model availability and rate limits.
- Confirm the backend's configured model is supported by your account.

### Port already in use

Check which process is using the required port before stopping anything. Do not terminate unfamiliar system or database processes.

### Empty dashboard

A new database may not contain the demo syllabus or job records from your original computer. Start with **Upload Syllabus**, then choose a saved job posting or paste a job description in **Job Market**. Creating a custom job profile and generating AI recommendations require a valid Groq API key. The frontend development server targets the local backend by default; set `VITE_API_BASE_URL` if you intend to use another backend.

## 9. Stop the Application

Press `Ctrl+C` in the frontend, backend, and Python service terminals. PostgreSQL can remain running as a Windows service.

## 10. Run Everything with Docker (optional)

Requires Docker Desktop. From the repository root:

```powershell
Copy-Item .env.example .env   # then edit .env: JWT_SECRET, GROQ_API_KEY, ADMIN_EMAILS
docker compose up --build
```

Open http://localhost:5173. The database (with pgvector) is created automatically and
kept in the `db-data` volume. Stop with `Ctrl+C`; remove data with `docker compose down -v`.

## Admin Access

Job catalog changes (CSV import, re-extracting skills of shared catalog jobs) need the
ADMIN role. Set `ADMIN_EMAILS` (comma-separated) before starting the backend; those
accounts become admins at startup or when they register.

## Security Notes

- Never commit database passwords, Groq API keys, or private credentials.
- Keep local secrets in environment variables or another secure configuration mechanism.
- Do not commit virtual environments such as `venv` or `node_modules`.
- Use a persistent, securely stored JWT secret for a deployed environment.
- Use HTTPS and appropriate production security settings before deployment.

## Repository

GitHub: https://github.com/Yogesh-t-yogi/CampusPulse
