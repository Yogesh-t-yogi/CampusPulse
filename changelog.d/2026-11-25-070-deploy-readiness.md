# 070 — Deployment readiness: email test, monitoring, logs, upload limits, CI

## What changed
- **Email:** `POST /api/admin/users/mail-test` (college / platform admin) sends a test email to yourself and
  shows the mail server's error if it fails. Button "Send test email" on My account. `/api/health/services`
  reports `emailConfigured`.
- **Monitoring:** `GET /api/health` now also checks the database: 200 `UP`, or 503 `DOWN` when the database
  does not answer (for UptimeRobot / Better Stack / cron). Rotated log file `logs/campuspulse.log`
  (10 MB per file, 14 days, 200 MB total; `LOG_FILE`, `LOG_MAX_FILE_SIZE`, `LOG_MAX_DAYS`, `LOG_TOTAL_SIZE`).
  `logs/` is git-ignored.
- **Upload limits:** `frontend/nginx.conf` and the new `deploy/nginx-site.conf` (HTTPS site example with
  `/api/` proxy, 300 s AI timeouts) allow 12 MB requests for 10 MB syllabus PDFs.
- **docker-compose.yml:** database and embedding service bound to `127.0.0.1` only; new settings
  (`SELF_REGISTRATION`, `DEFAULT_USER_PASSWORD`, `MAIL_*`, `APP_URL`) passed to the backend; `./logs` volume.
- **CI:** Maven download cached between runs and retried up to 3 times; actions updated
  (checkout v5, setup-java v5, setup-node v5, setup-python v6).
- README "Deploying" section. 164 backend tests pass; frontend lint and build pass.
