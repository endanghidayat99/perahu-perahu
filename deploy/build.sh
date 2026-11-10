#!/usr/bin/env bash
# Build image Docker lalu kemas menjadi satu berkas rilis yang siap disalin ke server
# (server tidak perlu kode sumber, registry, atau akses internet).
#
#   ./deploy/build.sh [VERSI] [user@server[:/folder/tujuan]]
#
# Contoh:
#   ./deploy/build.sh                           -> dist/perahu-perahu-<isi VERSION>.tar.gz
#   ./deploy/build.sh 1.1.0                     -> dist/perahu-perahu-1.1.0.tar.gz
#   ./deploy/build.sh 1.1.0 root@10.0.0.5       -> sekaligus salin & deploy ke server
#
# Variabel opsional: PLATFORM = arsitektur SERVER tujuan (default linux/amd64; server ARM: linux/arm64),
#                    APP_DIR di server (default /opt/perahu-perahu),
#                    MAVEN_MIRROR_URL / NPM_REGISTRY (mirror internal saat build).
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION="${1:-$(cat VERSION)}"
TARGET="${2:-}"
PLATFORM="${PLATFORM:-linux/amd64}"
APP_DIR="${APP_DIR:-/opt/perahu-perahu}"
IMAGE="perahu-perahu"
NAME="perahu-perahu-$VERSION"
OUT="dist/$NAME"

echo "==> Build image $IMAGE:$VERSION ($PLATFORM)"
docker buildx build --platform "$PLATFORM" --load --pull \
  --build-arg "MAVEN_MIRROR_URL=${MAVEN_MIRROR_URL:-}" --build-arg "NPM_REGISTRY=${NPM_REGISTRY:-}" \
  -t "$IMAGE:$VERSION" .
ARCH="$(docker image inspect -f '{{.Architecture}}' "$IMAGE:$VERSION")"
[ "linux/$ARCH" = "$PLATFORM" ] || { echo "Image $IMAGE:$VERSION berarsitektur $ARCH, bukan $PLATFORM"; exit 1; }

echo "==> Simpan image"
rm -rf "$OUT" && mkdir -p "$OUT"
docker save --platform "$PLATFORM" "$IMAGE:$VERSION" | gzip > "$OUT/images.tar.gz"

echo "==> Kemas berkas rilis"
cp docker-compose.yml .env.example deploy/deploy.sh "$OUT/"
echo "$VERSION" > "$OUT/VERSION"
(cd "$OUT" && (sha256sum images.tar.gz 2>/dev/null || shasum -a 256 images.tar.gz) > SHA256SUMS)
tar -C dist -czf "dist/$NAME.tar.gz" "$NAME"
rm -rf "$OUT"
echo "==> Selesai: dist/$NAME.tar.gz ($(du -h "dist/$NAME.tar.gz" | cut -f1))"

[ -n "$TARGET" ] || exit 0
HOST="${TARGET%%:*}"
[ "$HOST" != "$TARGET" ] && APP_DIR="${TARGET#*:}"
echo "==> Salin ke $HOST dan deploy ke $APP_DIR"
scp "dist/$NAME.tar.gz" "$HOST:/tmp/"
ssh -t "$HOST" "set -e; cd /tmp && tar -xzf $NAME.tar.gz && sudo APP_DIR=$APP_DIR ./$NAME/deploy.sh && rm -rf $NAME $NAME.tar.gz"
