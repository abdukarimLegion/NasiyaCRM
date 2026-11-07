#!/bin/sh
# Foydalanish: ./deploy/restore.sh backups/nasiya_2026-10-01_0200.dump
# DIQQAT: joriy bazadagi ma'lumotlar almashtiriladi!
#
# Compose fayli avtomatik topiladi:
#   - COMPOSE_FILE env berilgan bo'lsa — o'sha ishlatiladi;
#   - ishlab turgan "nasiya" backend qaysi fayl bilan ko'tarilgan bo'lsa — o'sha;
#   - aks holda lokalda docker-compose.yml, serverda docker-compose.prod.yml.
set -eu

FILE="$1"
[ -f "$FILE" ] || { echo "Fayl topilmadi: $FILE"; exit 1; }

DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
FILE=$(CDPATH= cd -- "$(dirname -- "$FILE")" && pwd)/$(basename -- "$FILE")

if [ -z "${COMPOSE_FILE:-}" ]; then
  for f in "$DIR/docker-compose.prod.yml" "$DIR/docker-compose.yml"; do
    [ -f "$f" ] || continue
    if [ -n "$(docker compose -f "$f" ps -q backend 2>/dev/null || true)" ]; then
      COMPOSE_FILE="$f"
      break
    fi
  done
fi
if [ -z "${COMPOSE_FILE:-}" ]; then
  if [ -f "$DIR/docker-compose.yml" ]; then
    COMPOSE_FILE="$DIR/docker-compose.yml"
  elif [ -f "$DIR/docker-compose.prod.yml" ]; then
    COMPOSE_FILE="$DIR/docker-compose.prod.yml"
  else
    echo "XATO: $DIR da docker-compose.yml yoki docker-compose.prod.yml topilmadi"
    exit 1
  fi
fi

echo "Compose fayli: $COMPOSE_FILE"
compose() { docker compose -f "$COMPOSE_FILE" "$@"; }

compose stop backend
compose exec -T db sh -c 'pg_restore --clean --if-exists -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < "$FILE"
compose start backend
echo "Tiklandi: $FILE"
