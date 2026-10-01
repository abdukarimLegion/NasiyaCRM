#!/usr/bin/env bash
# Serverda GitHub Actions (deploy.yml) tomonidan SSH orqali ishga tushiriladi.
#
# Serverdagi boshqa loyihalarga TEGMAYDI:
#   - faqat "nasiya" compose loyihasini yangilaydi (down / system prune / --remove-orphans yo'q);
#   - HTTP_PORT band bo'lsa (boshqa servis yoki konteyner) hech narsani to'xtatmaydi,
#     xato bilan chiqadi — boshqa port tanlash kerak;
#   - ghcr.io login vaqtinchalik DOCKER_CONFIG da, serverdagi ~/.docker/config.json o'zgarmaydi;
#   - faqat shu loyihaning eski image'lari o'chiriladi.
#
# Kerakli env: DEPLOY_PATH, BACKEND_IMAGE, FRONTEND_IMAGE, GHCR_USER.
# ghcr.io token stdin orqali beriladi (process ro'yxatida ko'rinmasligi uchun).
set -euo pipefail

: "${DEPLOY_PATH:?}" "${BACKEND_IMAGE:?}" "${FRONTEND_IMAGE:?}"
GHCR_TOKEN=""
read -r GHCR_TOKEN || true

cd "$DEPLOY_PATH"
test -f .env || { echo "XATO: $DEPLOY_PATH/.env topilmadi (.env.example asosida yarating yoki ENV_FILE secret'ini bering)"; exit 1; }

export BACKEND_IMAGE FRONTEND_IMAGE
compose() { docker compose -f docker-compose.prod.yml "$@"; }

env_get() { { grep -E "^$1=" .env || true; } | tail -n1 | cut -d= -f2- | tr -d "\"' \r"; }
HTTP_PORT="$(env_get HTTP_PORT)"
HTTP_PORT="${HTTP_PORT:-8088}"

# ---- 1. Port tekshiruvi: band bo'lsa boshqa servisni o'chirmaymiz, to'xtaymiz ----
port_used_by_others() {
  local port="$1" ours
  ours="$(compose ps -q frontend 2>/dev/null || true)"
  if [ -n "$ours" ] && docker port "$ours" 80/tcp 2>/dev/null | grep -qE ":${port}\$"; then
    return 1   # portni o'zimizning frontend konteynerimiz ushlab turibdi
  fi
  if command -v ss >/dev/null 2>&1 && ss -Hltn "sport = :${port}" | grep -q .; then
    return 0
  fi
  [ -n "$(docker ps -q --filter "publish=${port}")" ]
}

if port_used_by_others "$HTTP_PORT"; then
  echo "XATO: ${HTTP_PORT}-port serverda boshqa dastur/konteyner tomonidan band."
  command -v ss >/dev/null 2>&1 && ss -Hltnp "sport = :${HTTP_PORT}" || true
  docker ps --filter "publish=${HTTP_PORT}" --format '  {{.Names}}  {{.Ports}}' || true
  echo "Hech narsa to'xtatilmadi. $DEPLOY_PATH/.env da bo'sh HTTP_PORT tanlang va qayta deploy qiling."
  exit 1
fi
echo "Port ${HTTP_PORT} bo'sh yoki nasiya'ning o'ziniki — davom etamiz."

# ---- 2. Image'larni tortish (vaqtinchalik docker config bilan) ----
DOCKER_CONFIG="$(mktemp -d)"
export DOCKER_CONFIG
trap 'rm -rf "$DOCKER_CONFIG"' EXIT
# compose plugin ~/.docker/cli-plugins da o'rnatilgan bo'lsa, u ham topilsin
if [ -d "$HOME/.docker/cli-plugins" ]; then
  ln -s "$HOME/.docker/cli-plugins" "$DOCKER_CONFIG/cli-plugins"
fi
if [ -n "$GHCR_TOKEN" ]; then
  printf '%s' "$GHCR_TOKEN" | docker login ghcr.io -u "${GHCR_USER:?}" --password-stdin
fi

compose pull
compose up -d

# ---- 3. Sog'lomlik tekshiruvi ----
healthy=false
for _ in $(seq 1 36); do
  if compose exec -T backend sh -c 'curl -fsS http://localhost:8080/actuator/health' 2>/dev/null \
       | grep -q '"status":"UP"'; then
    healthy=true
    break
  fi
  sleep 5
done
if [ "$healthy" != true ]; then
  echo "XATO: backend 180 soniyada ko'tarilmadi:"
  compose logs --tail 80 backend
  exit 1
fi
echo "Backend UP. Tizim: http://SERVER_IP:${HTTP_PORT}"

# ---- 4. Faqat shu loyihaning eski image'larini tozalash (oxirgi 3 tasi qoladi) ----
for img in "$BACKEND_IMAGE" "$FRONTEND_IMAGE"; do
  docker images "${img%:*}" --format '{{.Repository}}:{{.Tag}}' \
    | grep -vxF "$img" | tail -n +3 | xargs -r docker rmi >/dev/null 2>&1 || true
done

compose ps
