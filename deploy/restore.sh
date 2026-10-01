#!/bin/sh
# Foydalanish: ./deploy/restore.sh backups/nasiya_2026-10-01_0200.dump
# DIQQAT: joriy bazadagi ma'lumotlar almashtiriladi!
set -eu
FILE="$1"
[ -f "$FILE" ] || { echo "Fayl topilmadi: $FILE"; exit 1; }
docker compose stop backend
docker compose exec -T db sh -c 'pg_restore --clean --if-exists -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < "$FILE"
docker compose start backend
echo "Tiklandi: $FILE"
