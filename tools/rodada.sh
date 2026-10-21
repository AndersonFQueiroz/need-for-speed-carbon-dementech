#!/usr/bin/env bash
# Dementech · NFS Carbon — rodada de identificação de um drift.
#
# O log do motor NÃO tem o ID da missão, mas o SAVE tem: quando a corrida
# TERMINA (vencer ou perder), o jogo reescreve o registro da área — a flag
# 0x01 "tentada" acende e o owner_a vira um hash novo. Validado com dado real
# (PROBE_T -> PROBE_V: áreas 18, 36 e 64).
#
#   ./tools/rodada.sh "<nome-da-missão>"
#
# O que o script faz:
#   1. checa se o jogo está fechado (o log do motor para de crescer)
#   2. ABRE o launcher Dementech (o sync exporta o save atual) e espera
#   3. baixa o export do aparelho (saveio/out/ALIAS_ANDERSON)
#   4. difa contra a base fixa da identificação (ronda_0.device)
#   5. aponta a área que mudou e imprime o comando para registrar o nome
#
# O que o DONO precisa fazer (uma missão por rodada):
#   jogar a missão até o fim (vencer ou perder) -> salvar no menu ->
#   fechar o jogo. O script cuida do resto.
#
# SPDX-License-Identifier: MIT
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PKG="com.nfscarbon.android"
REMOTE="/sdcard/Android/data/com.nfscarbon.android/files/saveio/out/ALIAS_ANDERSON"
LOG="/sdcard/Android/data/com.nfscarbon.android/files/logs/carbon.log"
WORK="${WORK:-/tmp/opencode}"

nome="${1:?informe o nome da missao (ex: Kimei Temple)}"

mkdir -p "$WORK"
# base FIXA da identificação: o primeiro export limpo. Nao pode ser sobrescrita
# por rodada (senao o diff perde a referencia).
base="${BASE:-$WORK/ronda_0.device}"
# a identificacao difa contra a RODADA ANTERIOR (o que mudou nesta rodada);
# a base fixa (ronda_0) segue sendo a referencia para a forja final.
# proximo indice = MAIOR indice existente + 1 (contar arquivos quebra se um
# numero faltar no meio).
max=0
for f in "$WORK"/ronda_*.device; do
  [ -e "$f" ] || continue
  i="${f##*ronda_}"; i="${i%.device}"
  if [ "$i" -gt "$max" ]; then max="$i"; fi
done
novo="$WORK/ronda_$((max + 1)).device"
if [ "$max" -gt 0 ]; then
  base="$WORK/ronda_$max.device"
fi

echo "=== 1. o jogo esta fechado? ==="
# o motor loga a cada ~10 s enquanto renderiza; o launcher nao escreve no log
log_mtime=$(adb shell "stat -c %Y $LOG" 2>/dev/null | tr -d '\r' | tail -1 || echo 0)
agora=$(date +%s)
if [ "$log_mtime" -gt 0 ] && [ $((agora - log_mtime)) -lt 45 ]; then
  echo "!! o JOGO AINDA ESTA RODANDO (log do motor ativo ha $((agora - log_mtime))s)"
  echo "   feche o jogo e rode de novo."
  exit 2
fi
echo "jogo fechado ok"

echo
echo "=== 2. sync + baixando export do aparelho ==="
# abrir o launcher dispara SaveIO.sync() -> exporta o save atual para saveio/out
adb shell "am start -n $PKG/.MainActivity" >/dev/null
sleep 4
adb pull "$REMOTE" "$novo" >/dev/null
echo "salvo: $novo"

if [ ! -f "$base" ]; then
  cp "$novo" "$base"
  echo
  echo "=== base da identificação salva: $base ==="
  echo "Agora: jogue UMA missao ate o fim, salve no menu e feche o jogo."
  echo "e rode de novo:  ./tools/rodada.sh \"$nome\""
  exit 0
fi

echo
echo "=== diff da ronda: base -> agora ==="
if ! python3 "$HERE/zerar_drift.py" diff "$base" "$novo"; then
  exit 1
fi

echo
echo "=== registrando o mapeamento ==="
idx=$(PYTHONPATH="$HERE" python3 - "$base" "$novo" <<'PY'
import sys
import savefmt as F
a = open(sys.argv[1], "rb").read(); b = open(sys.argv[2], "rb").read()
ganhou = []
for i in range(F.AREA_COUNT):
    fa = F.area_record(a, i)[2]
    fb = F.area_record(b, i)[2]
    if not (fa & 0x01) and (fb & 0x01):
        ganhou.append(i)
print(ganhou[0] if len(ganhou) == 1 else "")
PY
)
if [ -n "$idx" ]; then
  echo "uma unica area ganhou a tentativa 0x01: $idx -> registrando"
  python3 "$HERE/zerar_drift.py" mapear "$nome" "$idx"
else
  echo "use: python3 tools/zerar_drift.py mapear \"$nome\" <indice>"
fi
