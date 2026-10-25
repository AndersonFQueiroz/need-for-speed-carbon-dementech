#!/usr/bin/env bash
# Dementech · NFS Carbon — gera os assets de mídia do launcher a partir da SUA
# cópia do jogo (nenhum vídeo/áudio do jogo é versionado neste repositório).
#
# Uso: tools/make_launcher_media.sh "/caminho/para/NFSCARBON"
#   ou: NFSCARBON_DIR=... tools/make_launcher_media.sh
#
# Entrada : $DIR/Movies/attract_english_pal.wmv  (atrator original, 1280x720)
# Saída   : app/src/main/assets/launcher/bg.mp4   (~3 MB, 24 s, sem áudio)
#           app/src/main/assets/launcher/theme.m4a (~1 MB, trilha completa)
#
# Requer ffmpeg com libx264 e aac. O vídeo é recortado de um trecho de estrada
# noturna e fica em loop no launcher; o áudio é a trilha do atrator.
set -euo pipefail
cd "$(dirname "$0")/.."

DIR="${1:-${NFSCARBON_DIR:-}}"
if [ -z "$DIR" ]; then
  echo "Informe a pasta da cópia do jogo (contém Movies/, NFS/, default.xex)." >&2
  exit 1
fi
SRC="$DIR/Movies/attract_english_pal.wmv"
if [ ! -f "$SRC" ]; then
  echo "Não encontrei $SRC" >&2
  exit 1
fi

OUT="app/src/main/assets/launcher"
mkdir -p "$OUT"

ffmpeg -hide_banner -loglevel error -y -ss 26 -t 24 -i "$SRC" \
  -vf "scale=1280:-2,fps=30" -c:v libx264 -preset slow -crf 31 \
  -profile:v high -pix_fmt yuv420p -movflags +faststart -an "$OUT/bg.mp4"

ffmpeg -hide_banner -loglevel error -y -i "$SRC" -vn -c:a aac -b:a 112k -ac 2 \
  "$OUT/theme.m4a"

ls -l "$OUT"
