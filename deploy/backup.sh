#!/bin/sh
# Har kuni 02:00 da (Toshkent) pg_dump oladi va eski nusxalarni o'chiradi.
set -eu
KEEP="${BACKUP_KEEP_DAYS:-14}"

backup() {
  f="/backups/nasiya_$(date +%Y-%m-%d_%H%M).dump"
  echo "[backup] $f"
  pg_dump -Fc -f "$f.tmp" && mv "$f.tmp" "$f"
  find /backups -name 'nasiya_*.dump' -mtime +"$KEEP" -delete
}

backup   # konteyner ishga tushganda bir marta
while true; do
  now=$(date +%s)
  next=$(date -d "$(date +%Y-%m-%d) 02:00" +%s 2>/dev/null || echo 0)
  [ "$next" -le "$now" ] && next=$((next + 86400))
  [ "$next" -le 86400 ] && next=$((now + 86400))   # busybox date -d bo'lmasa: 24 soatdan keyin
  sleep $((next - now))
  backup
done
