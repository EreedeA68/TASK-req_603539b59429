#!/bin/bash
# Restore script with traceability logging

BACKUP_FILE=$1
LOG_FILE="/var/log/restore.log"

if [ -z "$BACKUP_FILE" ]; then
    echo "Usage: $0 <backup_file.sql.gz>"
    echo "Available backups:"
    ls -lh /backup/*.sql.gz 2>/dev/null
    exit 1
fi

if [ ! -f "$BACKUP_FILE" ]; then
    echo "[$(date)] ERROR: Backup file not found: $BACKUP_FILE"
    exit 1
fi

RESTORE_ID="restore-$(date +%Y%m%d_%H%M%S)-$$"
echo "[$(date)] RESTORE STARTED: id=$RESTORE_ID file=$BACKUP_FILE" | tee -a "$LOG_FILE"
echo "[$(date)] Restore initiated by: $(whoami)" | tee -a "$LOG_FILE"

gunzip -c "$BACKUP_FILE" | mysql \
    -h "${DB_HOST:-db}" \
    -u "${DB_USER:-meridian}" \
    -p"${DB_PASSWORD:-meridian_pass}" \
    "${DB_NAME:-meridianmart}" \
    2>>"$LOG_FILE"

if [ $? -eq 0 ]; then
    echo "[$(date)] RESTORE COMPLETED: id=$RESTORE_ID" | tee -a "$LOG_FILE"
else
    echo "[$(date)] RESTORE FAILED: id=$RESTORE_ID" | tee -a "$LOG_FILE"
    exit 1
fi
