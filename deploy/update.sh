#!/usr/bin/env bash
# Pull the latest code and rebuild/restart the stack. Run on the server from the project folder:
#   bash deploy/update.sh            (branch main)
#   bash deploy/update.sh feature/version2
set -euo pipefail
BRANCH="${1:-main}"

cd "$(dirname "$0")/.."
echo "== Backing up the database first"
bash deploy/backup-db.sh

echo "== Updating code ($BRANCH)"
git fetch origin
git checkout "$BRANCH"
git pull --ff-only origin "$BRANCH"

echo "== Rebuilding and restarting"
docker compose -f docker-compose.prod.yml up -d --build
docker image prune -f

echo "== Status"
docker compose -f docker-compose.prod.yml ps
