#!/usr/bin/env bash
set -euo pipefail

BACKUP_FILE="/docker-entrypoint-initdb.d/01_LAST_BCUP_BD.dump"

if [ ! -f "$BACKUP_FILE" ]; then
  echo "Backup file not found: $BACKUP_FILE"
  exit 1
fi

echo "Restoring PostgreSQL backup from $BACKUP_FILE ..."

if pg_restore \
  --format=tar \
  --verbose \
  --clean \
  --if-exists \
  --no-owner \
  --no-privileges \
  --exclude-schema=topology \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  "$BACKUP_FILE"; then
  echo "Backup restore completed."
else
  echo "pg_restore failed. Backup format might be plain SQL, not custom dump."
  exit 1
fi
