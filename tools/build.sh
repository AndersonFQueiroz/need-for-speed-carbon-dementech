#!/usr/bin/env bash
# Dementech · NFS Carbon — build completo: motor + APK assinado.
#
#   tools/build.sh [destino.apk]
#
# Requer: SDK Android (local.properties), JDK 17 e uma chave de assinatura.
# A chave NUNCA entra no repositório: aponte KS=/caminho/chave.jks.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

KS="${KS:-}"
KS_ALIAS="${KS_ALIAS:-t}"
KS_PASS="${KS_PASS:-}"
OUT="${1:-$ROOT/app/build/outputs/apk/release/app-release-signed.apk}"

if [ ! -f local.properties ]; then
  echo "✗ falta local.properties (sdk.dir=...)" >&2
  exit 1
fi

if [ ! -d app/src/main/jniLibs/arm64-v8a ]; then
  echo "→ baixando o motor (sha256 verificado)"
  tools/fetch_engine.sh
fi

echo "→ compilando"
./gradlew :app:assembleRelease

UNSIGNED="app/build/outputs/apk/release/app-release-unsigned.apk"
[ -f "$UNSIGNED" ] || { echo "✗ APK não gerado" >&2; exit 1; }

if [ -n "$KS" ]; then
  echo "→ assinando"
  apksigner sign --ks "$KS" --ks-key-alias "$KS_ALIAS" \
    --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
    --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true \
    --out "$OUT" "$UNSIGNED"
  apksigner verify --print-certs "$OUT" | head -3
  echo "→ $OUT"
else
  echo "→ APK sem assinatura: $UNSIGNED"
  echo "  (defina KS, KS_PASS e KS_ALIAS para assinar)"
fi
