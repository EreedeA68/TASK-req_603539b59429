#!/bin/bash
# Daily backup script — runs inside Docker container

BACKUP_DIR=/backup
RETENTION_DAYS=${BACKUP_RETENTION_DAYS:-30}
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="${BACKUP_DIR}/meridianmart_${TIMESTAMP}.sql.gz"
LOG_FILE="/var/log/backup.log"

echo "[$(date)] Starting full database backup..." | tee -a "$LOG_FILE"

mysqldump \
    -h "${DB_HOST:-db}" \
    -u "${DB_USER:-meridian}" \
    -p"${DB_PASSWORD:-meridian_pass}" \
    "${DB_NAME:-meridianmart}" \
    --single-transaction \
    --routines \
    --triggers \
    2>>"$LOG_FILE" | gzip > "$BACKUP_FILE"

if [ $? -eq 0 ]; then
    SIZE=$(du -sh "$BACKUP_FILE" | cut -f1)
    echo "[$(date)] Backup successful: $BACKUP_FILE (size: $SIZE)" | tee -a "$LOG_FILE"
else
    echo "[$(date)] ERROR: Backup failed!" | tee -a "$LOG_FILE"
    exit 1
fi

# Remove backups older than retention period
find "$BACKUP_DIR" -name "*.sql.gz" -mtime "+${RETENTION_DAYS}" -delete
echo "[$(date)] Removed backups older than ${RETENTION_DAYS} days." | tee -a "$LOG_FILE"

# List remaining backups
echo "[$(date)] Current backups:" | tee -a "$LOG_FILE"
ls -lh "$BACKUP_DIR"/*.sql.gz 2>/dev/null | tee -a "$LOG_FILE"
