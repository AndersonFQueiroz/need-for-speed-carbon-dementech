#!/usr/bin/env bash
# Dementech · NFS Carbon — coleta o log do motor do aparelho.
#
#   tools/collect_logs.sh [destino]
#
# Puxa carbon.log e os rotacionados de
# Android/data/com.nfscarbon.android/files/logs e roda a analise.
set -euo pipefail

DEST="${1:-/tmp/nfs-carbon-logs}"
PKG=com.nfscarbon.android
REMOTE="/sdcard/Android/data/$PKG/files/logs"

mkdir -p "$DEST"
echo "→ coletando de $REMOTE"
adb shell "ls $REMOTE" 2>/dev/null || { echo "sem logs no aparelho"; exit 1; }
for name in $(adb shell "ls $REMOTE" 2>/dev/null | tr -d '\r'); do
  case "$name" in
    carbon*.log) adb pull "$REMOTE/$name" "$DEST/$name" >/dev/null 2>&1 && echo "  $name" ;;
  esac
done

echo
echo "→ análise por sessão"
python3 "$(dirname "$0")/diagnose_freeze.py" --sessions "$DEST"/carbon*.log

echo
echo "logs em: $DEST"
