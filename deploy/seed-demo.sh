#!/usr/bin/env bash
# Demo (namoyish) ma'lumotlarini yuklaydi yoki o'chiradi.
#
#   ./deploy/seed-demo.sh            # yuklash — idempotent, qayta ishga tushirish xavfsiz
#   ./deploy/seed-demo.sh --remove   # faqat demo yozuvlarni o'chiradi
#
# Docker compose fayli avtomatik aniqlanadi (prod, keyin dev).
# Kerak bo'lsa COMPOSE_FILE bilan aniq ko'rsatish mumkin:
#   COMPOSE_FILE=docker-compose.prod.yml ./deploy/seed-demo.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

ACTION=seed
case "${1:-}" in
  ""|--seed|seed)    ACTION=seed ;;
  --remove|remove)   ACTION=remove ;;
  -h|--help)
    sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//'
    exit 0
    ;;
  *)
    echo "Foydalanish: $0 [--remove]" >&2
    exit 2
    ;;
esac

FILE="$ROOT/deploy/seed/demo_${ACTION}.sql"
[ -f "$FILE" ] || { echo "XATO: $FILE topilmadi" >&2; exit 1; }

COMPOSE_FILE="${COMPOSE_FILE:-}"
if [ -z "$COMPOSE_FILE" ]; then
  for f in docker-compose.prod.yml docker-compose.yml; do
    if [ -f "$ROOT/$f" ]; then COMPOSE_FILE="$f"; break; fi
  done
fi
[ -n "$COMPOSE_FILE" ] || { echo "XATO: docker-compose fayli topilmadi" >&2; exit 1; }

compose() { docker compose -f "$COMPOSE_FILE" "$@"; }

if ! compose exec -T db sh -c 'true' >/dev/null 2>&1; then
  echo "XATO: db konteyneri ishlamayapti." >&2
  echo "Avval: docker compose -f $COMPOSE_FILE up -d db" >&2
  exit 1
fi

echo "Demo ma'lumot: $ACTION ($COMPOSE_FILE)"
compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < "$FILE"
echo "Tayyor."
