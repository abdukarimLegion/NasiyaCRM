#!/bin/sh
# Foydalanish (loyiha papkasidan, masalan /opt/nasiya):
#   sh deploy/restore.sh backups/nasiya_2026-10-01_0200.dump
# DIQQAT: joriy bazadagi ma'lumotlar almashtiriladi!
set -eu
FILE="${1:-}"
[ -n "$FILE" ] && [ -f "$FILE" ] || { echo "Fayl topilmadi: ${FILE:-<berilmagan>}"; exit 1; }
FILE="$(cd "$(dirname "$FILE")" && pwd)/$(basename "$FILE")"
cd "$(dirname "$0")/.."

if [ -f docker-compose.prod.yml ]; then
  # Serverda: image nomlari faqat pull/up uchun kerak, bu yerda ishlab turgan konteynerlar ishlatiladi
  export BACKEND_IMAGE="${BACKEND_IMAGE:-unused}" FRONTEND_IMAGE="${FRONTEND_IMAGE:-unused}"
  compose() { docker compose -f docker-compose.prod.yml "$@"; }
else
  compose() { docker compose "$@"; }
fi

compose stop backend
compose exec -T db sh -c 'pg_restore --clean --if-exists -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < "$FILE"
compose start backend
echo "Tiklandi: $FILE"
