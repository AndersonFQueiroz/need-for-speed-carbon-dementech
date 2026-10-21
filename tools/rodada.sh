#!/usr/bin/env bash
# Dementech · NFS Carbon — rodada de identificação de um drift.
#
# O log do motor NÃO tem o ID da missão, mas o SAVE tem: quando o jogador entra
# numa corrida, o jogo reescreve o registro daquela área (flag 0x01 "tentada").
# Diffing dois saves mostra exatamente qual área mudou — e essa é a missão em
# que o jogador entrou. Validado com dado real (PROBE_T -> PROBE_V).
#
#   ./tools/rodada.sh <nome-da-missão>
#
# O que o script faz:
#   1. baixa o export do aparelho (saveio/out/ALIAS_ANDERSON)
#   2. difa contra a base da rodada anterior
#   3. aponta a área que mudou
#   4. registra nome -> índice em tools/drift_map.json
#
# Pré-requisito no aparelho: a corrida foi jogada (e o jogo gravou o save);
# depois o launcher Dementech foi aberto — é ele que exporta.
#
# SPDX-License-Identifier: MIT
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REMOTE="/sdcard/Android/data/com.nfscarbon.android/files/saveio/out/ALIAS_ANDERSON"
WORK="${WORK:-/tmp/opencode}"

nome="${1:?informe o nome da missao (ex: Kimei Temple)}"

mkdir -p "$WORK"
n=$(ls "$WORK"/ronda_*.device 2>/dev/null | wc -l)
base="$WORK/ronda_$n.device"
novo="$WORK/ronda_$((n + 1)).device"

echo "=== baixando export do aparelho ==="
adb pull "$REMOTE" "$novo" >/dev/null
echo "salvo: $novo"

if [ ! -f "$base" ]; then
  cp "$novo" "$base"
  echo
  echo "=== base da identificação salva: $base ==="
  echo "Agora: jogue UMA missão, feche o jogo, abra o launcher Dementech"
  echo "e rode de novo:  ./tools/rodada.sh \"$nome\""
  exit 0
fi

echo
echo "=== diff da ronda: base -> agora ==="
python3 "$HERE/zerar_drift.py" diff "$base" "$novo" || exit 1

echo
echo "=== registrando o mapeamento ==="

echo "use: python3 tools/zerar_drift.py mapear \"$nome\" <indice>"
