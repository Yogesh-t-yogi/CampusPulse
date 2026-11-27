# 072 — AWS deployment (one EC2 server)

- `docker-compose.prod.yml`: Caddy (automatic HTTPS) in front of the frontend and `/api/*` → backend;
  database, backend and embedding service not published; restart policies; production settings (sign-up
  off, no demo users, CORS = `APP_URL`); `./logs` volume; Java memory limit.
- `deploy/Caddyfile`: HTTPS by domain (or `:80` by IP), 12 MB uploads, 300 s AI timeouts, security headers.
- `deploy/setup-server.sh` (Docker, swap, automatic security updates on Ubuntu 24.04),
  `deploy/update.sh` (backup, pull, rebuild), `deploy/backup-db.sh` (daily pg_dump, 14 days, optional S3).
- `deploy/env.production.example` and `deploy/AWS-DEPLOY.md` (step-by-step: instance, security group,
  Elastic IP, domain, .env, start, backups, monitoring, updates, cost).
- `.gitattributes`: shell scripts and Caddyfile keep LF line endings.
