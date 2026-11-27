# Deploying CampusPulse on AWS (one EC2 server)

Everything runs on one EC2 instance with Docker Compose (`docker-compose.prod.yml`):

```
Internet ──► Caddy :80/:443 (HTTPS) ──► frontend (React, Nginx)
                                   └──► /api/* ──► backend (Spring Boot) ──► db (PostgreSQL + pgvector)
                                                                        └──► ai-service (embeddings)
```

Only Caddy is reachable from the internet. Caddy gets a free HTTPS certificate for your domain by itself.

## Cost (Mumbai region, ap-south-1, approximate)

| Item | Choice | About |
|---|---|---|
| EC2 | **t3.medium** (2 vCPU, 4 GB RAM) | US$ 30 / month if running 24×7 |
| Disk | 30 GB gp3 | US$ 2.5 / month |
| Elastic IP | 1, attached to the running instance | about US$ 3.6 / month (AWS charges every public IPv4) |
| Domain | optional, any registrar | ₹ 100–1,000 / year |

Stop the instance when you do not need it (you then pay only for the disk and IP). The free-tier t2/t3.micro
(1 GB RAM) is **too small**: Java, PostgreSQL and the embedding model need about 3 GB together.
New AWS accounts may have free credits; check **Billing → Credits**.

## 1. AWS account safety (once)

1. Sign in to the AWS console with the root account, turn on **MFA** for it.
2. **Billing → Budgets → Create budget** → monthly cost budget, e.g. US$ 40, email alert at 80%.
3. Use the region **Asia Pacific (Mumbai) ap-south-1** (top right) for everything below.

## 2. Launch the server

**EC2 → Instances → Launch instance**

| Setting | Value |
|---|---|
| Name | `campuspulse` |
| Image (AMI) | **Ubuntu Server 24.04 LTS** (64-bit x86) |
| Instance type | **t3.medium** |
| Key pair | Create new → `campuspulse-key`, type RSA, format `.pem` → it downloads; keep it safe |
| Network → Security group | Create: allow **SSH 22 from "My IP"**, **HTTP 80 from anywhere**, **HTTPS 443 from anywhere** |
| Storage | **30 GiB gp3** |

Do **not** open 5432, 8000 or 8081: the database, embedding service and backend stay private.

Then **EC2 → Elastic IPs → Allocate** → select it → **Associate** with the `campuspulse` instance.
This address stays the same across restarts. Note it (example `13.233.10.20`).

## 3. Domain (recommended, for HTTPS)

At your domain registrar add an **A record**: `campuspulse` → your Elastic IP
(so `campuspulse.yourdomain.in` points to the server). Wait until `ping campuspulse.yourdomain.in` shows the IP.

No domain? You can still start with `http://<elastic-ip>` (step 5, `SITE_ADDRESS=:80`).
Passwords then travel without encryption, so use this only for a short demo.

## 4. Connect and prepare the server

From your laptop (PowerShell or Git Bash), in the folder with the key:

```bash
ssh -i campuspulse-key.pem ubuntu@13.233.10.20
```

(Windows: if it complains about key permissions, right-click the .pem → Properties → Security → leave only your user.)

On the server:

```bash
git clone https://github.com/Yogesh-t-yogi/CampusPulse.git
cd CampusPulse
bash deploy/setup-server.sh
exit
```

Log in again with the same `ssh` command (so Docker works without `sudo`).

## 5. Settings (.env) on the server

```bash
cd CampusPulse
cp deploy/env.production.example .env
openssl rand -base64 24     # copy as DB_PASSWORD
openssl rand -base64 48     # copy as JWT_SECRET
nano .env                   # fill the values, Ctrl+O to save, Ctrl+X to exit
chmod 600 .env
```

Fill at least:

| Key | Value |
|---|---|
| `SITE_ADDRESS` | `campuspulse.yourdomain.in` (or `:80` without a domain) |
| `APP_URL` | `https://campuspulse.yourdomain.in` (or `http://<elastic-ip>`) |
| `DB_PASSWORD`, `JWT_SECRET` | the two random values from above |
| `ADMIN_EMAILS` | your email (the platform admin account) |
| `DEFAULT_USER_PASSWORD` | 8+ characters; first password of the platform admin |
| `GEMINI_API_KEY`, `GROQ_API_KEY`, `LLAMA_CLOUD_API_KEY` | production keys (create new ones, separate from your laptop) |
| `MAIL_*` | the same mail settings that work on your laptop |

## 6. Start

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

The first build takes 5–10 minutes. Then:

```bash
docker compose -f docker-compose.prod.yml ps          # all services "running"/"healthy"
docker compose -f docker-compose.prod.yml logs -f backend   # wait for "Started CampuspulseBackendApplication"
curl -s localhost/api/health                          # {"status":"UP",...}
```

Open `https://campuspulse.yourdomain.in` (or `http://<elastic-ip>`), sign in with the `ADMIN_EMAILS` address
and `DEFAULT_USER_PASSWORD`, and set your own password. Then **My account → Send test email**.

Onboard a college: **Organisation** → add the college → choose it in the top bar → **Users → Add user** (role
College admin). They receive the welcome email with the sign-in link.

## 7. Backups (do this the same day)

```bash
bash deploy/backup-db.sh                      # test once
crontab -e                                    # add the line below, save
30 2 * * * bash /home/ubuntu/CampusPulse/deploy/backup-db.sh >> /home/ubuntu/backup.log 2>&1
```

Backups go to `~/campuspulse-backups` (14 days kept). To also keep them off the server: create an S3 bucket,
give the instance an IAM role with `s3:PutObject` on it (**EC2 → Actions → Security → Modify IAM role**),
`sudo apt install awscli`, and set `BACKUP_S3_BUCKET=<bucket>` in `.env`.
Also take an occasional **EBS snapshot** (**EC2 → Volumes → Create snapshot**).

Restore a backup:

```bash
gunzip -c ~/campuspulse-backups/<file>.sql.gz | docker compose -f docker-compose.prod.yml exec -T db psql -U campuspulse -d campuspulse
```

## 8. Monitoring

- **Uptime:** free account at uptimerobot.com → HTTP(s) monitor on `https://<your site>/api/health`
  (alerts by email when it is not 200).
- **Logs:** `tail -f ~/CampusPulse/logs/campuspulse.log` (rotated: 10 MB × 14 days) or
  `docker compose -f docker-compose.prod.yml logs --tail=200 backend`.
- **Server:** EC2 → instance → **Monitoring** tab (CPU); `free -h` and `df -h` for memory and disk.

## 9. Updating to a new version

After merging changes into `main` on GitHub:

```bash
cd ~/CampusPulse
bash deploy/update.sh          # backup, git pull, rebuild, restart
```

Database changes are applied automatically by Flyway when the backend starts.

## 10. Stop / start to save money

**EC2 → Instances → Instance state → Stop** (data stays on the disk; the Elastic IP stays). **Start** again
later; the containers start by themselves (`restart: unless-stopped`).

## Troubleshooting

| Problem | Check |
|---|---|
| Site does not open | Security group has 80/443 open; `docker compose -f docker-compose.prod.yml ps`; `logs caddy` |
| No HTTPS certificate | The domain's A record must point to the Elastic IP **before** starting; ports 80 and 443 open; `logs caddy` |
| "Server error" on sign-in | `logs backend`: database password or `JWT_SECRET` missing in `.env` |
| Build stops / server freezes | Out of memory: use t3.medium (4 GB); swap is added by `setup-server.sh` |
| Upload fails for big PDFs | Limit is 10 MB per file (same as on the laptop) |
| AI features fail | Keys in `.env`; `logs backend` names the provider and its error |
