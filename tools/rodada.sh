#!/usr/bin/env bash
# Dementech · NFS Carbon — rodada de identificação de drift.
#
# O log do motor NÃO tem o ID da missão, mas o SAVE tem: quando o jogador entra
# numa corrida, o jogo reescreve o registro daquela área (flag 0x01 "tentada",
# owner_a vira o crew rival). Diffing dois saves mostra exatamente qual área
# mudou — e essa é a missão em que o jogador entrou.
#
#   ./tools/rodada.sh pull     <destino>              baixa o export do aparelho
#   ./tools/rodada.sh diff     <antes> <depois>        o que mudou
#   ./tools/rodada.sh mapa     <save>                 hexágonos e pendências
#
# Uso típico de uma rodada:
#   1. o jogador entra numa missão e sai
#   2. fecha o jogo e abre o launcher (o export é atualizado)
#   3. ./tools/rodada.sh pull /tmp/opencode/ronda_N.device
#   4. ./tools/rodada.sh diff  /tmp/opencode/base.device /tmp/opencode/ronda_N.device
#
# SPDX-License-Identifier: MIT
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REMOTE="/sdcard/Android/data/com.nfscarbon.android/files/saveio/out/ALIAS_ANDERSON"

cmd="${1:-}"; shift || true

case "$cmd" in
  pull)
    destino="${1:?informe o destino}"
    adb pull "$REMOTE" "$destino"
    echo "export do jogo: $destino"
    ;;
  diff)
    antes="${1:?}"; depois="${2:?}"
    python3 "$HERE/zerar_drift.py" diff "$antes" "$depois"
    ;;
  mapa)
    save="${1:?}"
    python3 "$HERE/zerar_drift.py" mapa "$save"
    ;;
  *)
    sed -n '2,20p' "${BASH_SOURCE[0]}"
    exit 1
    ;;
esac
