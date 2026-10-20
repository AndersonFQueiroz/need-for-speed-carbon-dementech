#!/usr/bin/env bash
# Dementech · NFS Carbon — busca o motor (binarios nativos) e os assets de shader
# do release oficial do port (NFS-CARBON-360-ANDROID-RECOMP v0.3.3) e valida o sha256.
#
# O motor NAO esta neste repositorio: ele e compilado a partir do executavel do
# Xbox 360, que nao pode ser distribuido. O repositorio publica so o launcher.
#
# Uso:  tools/fetch_engine.sh [destino]     (padrao: app/src/main/jniLibs/arm64-v8a)
set -euo pipefail

VERSION="v0.3.3"
APK="NFSCarbon-Android-v0.3.3-arm64.apk"
BASE="https://github.com/codepdbh/NFS-CARBON-360-ANDROID-RECOMP/releases/download/${VERSION}"
WORK="${TMPDIR:-/tmp}/dementech-engine"
DEST="${1:-app/src/main/jniLibs/arm64-v8a}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

mkdir -p "$WORK" "$DEST" "$ROOT/app/src/main/assets"

if [ ! -f "$WORK/$APK" ]; then
  echo "[1/3] baixando $APK ..."
  curl -fL --retry 3 -o "$WORK/$APK" "$BASE/$APK"
fi

if [ ! -f "$WORK/SHA256SUMS-v0.3.3.txt" ]; then
  echo "[2/3] baixando sums ..."
  curl -fL -o "$WORK/SHA256SUMS-v0.3.3.txt" "$BASE/SHA256SUMS-v0.3.3.txt"
fi
echo "[2/3] conferindo sha256 ..."
(cd "$WORK" && grep -F "$APK" SHA256SUMS-v0.3.3.txt | sha256sum -c -)

echo "[3/3] extraindo motor e shaders ..."
rm -rf "$WORK/x"; mkdir -p "$WORK/x"
unzip -q -o "$WORK/$APK" -d "$WORK/x"

# binarios nativos (arm64-v8a)
mkdir -p "$DEST"
cp "$WORK/x"/lib/arm64-v8a/*.so "$DEST"/

# assets de shader (compilados no aparelho pelo ShaderBuilder)
mkdir -p "$ROOT/app/src/main/assets/shaders"
cp -r "$WORK/x"/assets/shaders/. "$ROOT/app/src/main/assets/shaders"/ 2>/dev/null || true
cp "$WORK/x"/assets/DRIVER-NOTICES.txt "$ROOT/app/src/main/assets/" 2>/dev/null || true

echo "motor em $DEST:"
ls -1 "$DEST" | sed 's/^/  /'
echo "shaders em app/src/main/assets/shaders:"
ls -1 "$ROOT/app/src/main/assets/shaders" | sed 's/^/  /'
echo "OK"
