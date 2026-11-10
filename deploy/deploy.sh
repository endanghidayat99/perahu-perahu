#!/usr/bin/env bash
# Deploy rilis di server. Jalankan dari folder hasil ekstrak perahu-perahu-<versi>.tar.gz:
#
#   sudo ./deploy.sh                    deploy versi rilis ini
#   sudo ./deploy.sh --rollback 1.0.0   kembali ke image versi lama (yang masih ada di server)
#
# Konfigurasi (.env) di APP_DIR tidak pernah ditimpa.
set -euo pipefail
SRC="$(cd "$(dirname "$0")" && pwd)"
APP_DIR="${APP_DIR:-/opt/perahu-perahu}"
IMAGE="perahu-perahu"

command -v docker >/dev/null || { echo "Docker belum terpasang"; exit 1; }
docker compose version >/dev/null || { echo "Plugin docker compose belum terpasang"; exit 1; }

if [ "${1:-}" = "--rollback" ]; then
  VERSION="${2:?Sebutkan versi, contoh: --rollback 1.0.0}"
  cd "$APP_DIR"
else
  VERSION="$(cat "$SRC/VERSION")"
  echo "==> Verifikasi & muat image v$VERSION"
  (cd "$SRC" && (sha256sum -c SHA256SUMS 2>/dev/null || shasum -a 256 -c SHA256SUMS))
  gunzip -c "$SRC/images.tar.gz" | docker load

  echo "==> Pasang berkas ke $APP_DIR"
  mkdir -p "$APP_DIR"
  cp "$SRC/docker-compose.yml" "$SRC/.env.example" "$APP_DIR/"
  [ "$SRC" = "$APP_DIR" ] || cp "$SRC/deploy.sh" "$APP_DIR/deploy.sh"
  cd "$APP_DIR"

  if [ ! -f .env ]; then
    echo "==> .env belum ada, dibuat dari .env.example (port bawaan 8090)"
    cp .env.example .env
    chmod 600 .env
  fi
fi

# arsitektur image harus sama dengan server (mis. image amd64 tidak jalan di server ARM: "exec format error")
case "$(uname -m)" in x86_64|amd64) HOST_ARCH=amd64 ;; aarch64|arm64) HOST_ARCH=arm64 ;; *) HOST_ARCH="$(uname -m)" ;; esac
ARCH="$(docker image inspect -f '{{.Architecture}}' "$IMAGE:$VERSION" 2>/dev/null || true)"
if [ -n "$ARCH" ] && [ "$ARCH" != "$HOST_ARCH" ]; then
  echo "Image $IMAGE:$VERSION berarsitektur $ARCH, sedangkan server $HOST_ARCH."
  echo "Build ulang di mesin pengembang: PLATFORM=linux/$HOST_ARCH ./deploy/build.sh $VERSION"
  exit 1
fi

docker image inspect "$IMAGE:$VERSION" >/dev/null 2>&1 \
  || { echo "Image $IMAGE:$VERSION tidak ada di server"; exit 1; }

DC=(docker compose)
PREV="$(sed -n 's/^APP_VERSION=//p' .env)"
if grep -q '^APP_VERSION=' .env; then
  sed -i.bak "s|^APP_VERSION=.*|APP_VERSION=$VERSION|" .env && rm -f .env.bak
else
  echo "APP_VERSION=$VERSION" >> .env
fi

echo "==> Jalankan v$VERSION${PREV:+ (sebelumnya v$PREV)}"
if ! "${DC[@]}" up -d --pull never --remove-orphans --wait --wait-timeout 300; then
  "${DC[@]}" ps
  "${DC[@]}" logs --tail 80 app
  [ -n "$PREV" ] && echo "GAGAL. Rollback: sudo APP_DIR=$APP_DIR $APP_DIR/deploy.sh --rollback $PREV"
  exit 1
fi
"${DC[@]}" ps
PORT="$(sed -n 's/^APP_PORT=//p' .env)"
echo "==> Berhasil: v$VERSION aktif di $APP_DIR -> http://$(hostname -I 2>/dev/null | awk '{print $1}'):${PORT:-8090}"
echo "    Di belakang nginx/Cloudflare, teruskan header Upgrade/Connection untuk path /ws (lihat README.md)."
