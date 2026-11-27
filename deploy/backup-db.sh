#!/usr/bin/env bash
# Database backup: compressed pg_dump in ~/campuspulse-backups, keeps 14 days.
# Optional: set BACKUP_S3_BUCKET in .env (and give the EC2 instance an IAM role with s3:PutObject)
# to also copy each backup to Amazon S3.
# Daily at 02:30 via cron:  30 2 * * * bash /home/ubuntu/CampusPulse/deploy/backup-db.sh >> /home/ubuntu/backup.log 2>&1
set -euo pipefail

cd "$(dirname "$0")/.."
DIR="$HOME/campuspulse-backups"
mkdir -p "$DIR"
FILE="$DIR/campuspulse-$(date +%Y%m%d-%H%M%S).sql.gz"

docker compose -f docker-compose.prod.yml exec -T db pg_dump -U campuspulse -d campuspulse | gzip > "$FILE"
echo "Backup written: $FILE ($(du -h "$FILE" | cut -f1))"

find "$DIR" -name 'campuspulse-*.sql.gz' -mtime +14 -delete

BUCKET="$(grep -E '^BACKUP_S3_BUCKET=' .env 2>/dev/null | cut -d= -f2- || true)"
if [ -n "${BUCKET}" ] && command -v aws >/dev/null 2>&1; then
  aws s3 cp "$FILE" "s3://${BUCKET}/db/" && echo "Copied to s3://${BUCKET}/db/"
fi
