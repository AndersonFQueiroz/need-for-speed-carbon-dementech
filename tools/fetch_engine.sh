#!/usr/bin/env bash
# Dementech · NFS Carbon — instala o motor (binarios nativos) e os assets de shader
# a partir do APK do motor (arm64-v8a).
#
# O motor NAO esta neste repositorio: e binario de terceiros, compilado a partir do
# executavel do Xbox 360, e nao pode ser distribuido. O repositorio publica so o
# launcher.
#
# Uso:  ENGINE_APK=/caminho/engine-arm64.apk tools/fetch_engine.sh [destino]
#       (padrao: app/src/main/jniLibs/arm64-v8a)
#
# Variavel opcional:
#   SHA256SUMS  arquivo com as somas publicadas (mesmo formato do sha256sum -c);
#               se existir, a soma do APK e conferida antes de extrair.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="${1:-app/src/main/jniLibs/arm64-v8a}"

if [ -z "${ENGINE_APK:-}" ]; then
  if [ -f "$ROOT/tools/engine/engine-arm64.apk" ]; then
    ENGINE_APK="$ROOT/tools/engine/engine-arm64.apk"
  else
    echo "erro: defina ENGINE_APK com o caminho do APK do motor (arm64-v8a)." >&2
    echo "      esperado tambem em tools/engine/engine-arm64.apk" >&2
    exit 1
  fi
fi

[ -f "$ENGINE_APK" ] || { echo "erro: $ENGINE_APK nao existe" >&2; exit 1; }

APK_NAME="$(basename "$ENGINE_APK")"
echo "[1/3] APK do motor: $ENGINE_APK"
sha256sum "$ENGINE_APK"

if [ -n "${SHA256SUMS:-}" ] && [ -f "$SHA256SUMS" ]; then
  echo "[2/3] conferindo sha256 contra $(basename "$SHA256SUMS") ..."
  SUM="$(sha256sum "$ENGINE_APK" | cut -d' ' -f1)"
  if grep -qiF "$SUM" "$SHA256SUMS"; then
    echo "      OK"
  else
    echo "erro: sha256 nao confere com $SHA256SUMS" >&2
    exit 1
  fi
else
  echo "[2/3] sha256 nao conferido (defina SHA256SUMS para validar)"
fi

echo "[3/3] extraindo motor e shaders ..."
WORK="${TMPDIR:-/tmp}/dementech-engine"
rm -rf "$WORK/x"; mkdir -p "$WORK/x" "$DEST" "$ROOT/app/src/main/assets"
unzip -q -o "$ENGINE_APK" -d "$WORK/x"

cp "$WORK/x"/lib/arm64-v8a/*.so "$DEST"/

# caminhos de build embutidos nos .so: troca por um nome neutro de mesmo
# comprimento (offsets do ELF intactos)
python3 - "$DEST" <<'PY'
import glob, os, sys
for f in glob.glob(os.path.join(sys.argv[1], '*.so')):
    d = open(f, 'rb').read()
    if b'C:/Users/Sistemas' not in d:
        continue
    n = d.count(b'C:/Users/Sistemas')
    d2 = d.replace(b'C:/Users/Sistemas', b'C:/Users/Anderson')
    assert len(d2) == len(d), f
    open(f, 'wb').write(d2)
    print('   %s: %d caminhos' % (os.path.basename(f), n))
PY

mkdir -p "$ROOT/app/src/main/assets/shaders"
cp -r "$WORK/x"/assets/shaders/. "$ROOT/app/src/main/assets/shaders"/ 2>/dev/null || true
cp "$WORK/x"/assets/DRIVER-NOTICES.txt "$ROOT/app/src/main/assets/" 2>/dev/null || true

echo "motor em $DEST:"
ls -1 "$DEST" | sed 's/^/  /'
echo "shaders em app/src/main/assets/shaders:"
ls -1 "$ROOT/app/src/main/assets/shaders" | sed 's/^/  /'
echo "OK"
