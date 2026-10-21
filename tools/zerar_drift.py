#!/usr/bin/env python3
"""Dementech · NFS Carbon — zerar drift de freeroam pelo nome.

O save tem 73 registros de evento de freeroam (tabela em 0x4DC, 12 B cada) e
o mapa mostra 7 eventos por hexágono. Cada registro tem:

    owner_a  u32   crew que domina o evento (0x27182028 = 21ST STREET,
                   0x40693BA3 = BUSHIDO, 0xC49D46EF = JOGADOR)
    owner_b  u32   crew do outro lado
    flags    u8    0x40 visível no mapa · 0x01 tentada · 0x02 vencida
                   · 0x04 rival · 0x20 do jogador

O jogo reescreve o registro do evento **quando você entra na corrida** — é
isso que permite identificar o índice de cada missão pelo nome, sem chute:

    python3 tools/zerar_drift.py diff <save_antes> <save_depois>

O `diff` aponta as áreas que mudaram; a que mudou é a missão em que você
entrou. O mapeamento nome -> índice fica em tools/drift_map.json.

Comandos:
    mapa     <save>                      padrões por hexágono
    areas    <save>                      as 73 áreas com crew e estado
    diff     <antes> <depois>            o que mudou (identificação)
    marcar   <save> <out> <área>...      forja a vitória (formato nativo)
    resolver <save> <nome>               nome -> índice (usa drift_map.json)
    mapear   <nome> <índice>             registra nome -> índice
    puxar    <destino>                   baixa o save exportado do aparelho

SPDX-License-Identifier: MIT
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import savefmt as F  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
MAP_FILE = os.path.join(HERE, "drift_map.json")

CREWS = {
    0x40693BA3: "BUSHIDO (Kenji)",
    0x27182028: "21ST STREET (Angie)",
    0xA9382979: "WOLF",
    0x10A2F397: "DARIUS",
    0x97F3A210: "mapa vermelho (bloqueado)",
    0xC49D46EF: "JOGADOR",
    0xCF59E641: "crew ?6",
    0x3400C6F4: "crew ?7",
    0x1AB75EC8: "crew ?8",
    0x962A004C: "crew ?9",
    0xB6294EFE: "crew ?10",
}

# flags que o jogo preserva ao registrar uma vitória
KEEP_FLAGS = 0x01 | 0x04
WON = 0x02

REMOTE = "/sdcard/Android/data/com.nfscarbon.android/files/saveio/out/ALIAS_ANDERSON"


def describe(owner: int) -> str:
    return CREWS.get(owner, "0x%08X" % owner)


def flag_names(flags: int) -> str:
    names = []
    for bit, name in ((0x40, "visível"), (0x20, "jogador"), (0x04, "rival"),
                      (0x02, "vencida"), (0x01, "tentada")):
        if flags & bit:
            names.append(name)
    return ", ".join(names) or "-"


def areas(data: bytes):
    out = []
    for i in range(F.AREA_COUNT):
        owner_a, owner_b, flags = F.area_record(data, i)
        out.append({
            "i": i,
            "owner_a": owner_a,
            "owner_b": owner_b,
            "flags": flags,
            "won": bool(flags & 0x02),
            "visible": bool(flags & 0x40),
            "tried": bool(flags & 0x01),
            "crew": describe(owner_a),
        })
    return out


def pattern(items) -> str:
    """'.' = pendente, 'X' = vencido."""
    return "".join("X" if a["won"] else "." for a in items)


def cmd_mapa(path: str) -> int:
    items = areas(open(path, "rb").read())
    print("hexágonos (7 eventos cada) — X=vencido  .=pendente\n")
    for h in range(0, len(items), 7):
        grupo = items[h:h + 7]
        if len(grupo) < 7:
            print(f"  hex {h//7}: (incompleto: {len(grupo)} registros) {pattern(grupo)}")
            continue
        crews = sorted({a["crew"] for a in grupo if a["owner_a"] != F.PLAYER})
        print(f"  hex {h//7:>2}  areas {h:>2}-{h+6:<2}  {pattern(grupo)}  "
              f"vence {sum(1 for a in grupo if a['won'])}/7  crews: {', '.join(crews) or '-'}")
    return 0


def cmd_areas(path: str) -> int:
    for a in areas(open(path, "rb").read()):
        estado = "VENCIDA" if a["won"] else ("oculta" if not a["visible"] else "pendente")
        print(f"  area {a['i']:>2}  {a['crew']:<24} flags {a['flags']:02X}  {estado:<9} {flag_names(a['flags'])}")
    return 0


def cmd_diff(antes: str, depois: str) -> int:
    """Aponta as áreas que o jogo reescreveu — a identificação das missões."""
    a = open(antes, "rb").read()
    b = open(depois, "rb").read()
    if len(a) != len(b):
        print(f"!! tamanhos diferentes: {len(a)} vs {len(b)}")
        return 1
    aa = areas(a)
    ab = areas(b)
    mudou = [i for i in range(F.AREA_COUNT) if aa[i] != ab[i]]
    if not mudou:
        print("NENHUMA área mudou — a corrida NÃO foi registrada pelo jogo.\n")
        pend = [a for a in ab if a["visible"] and not a["won"]]
        print("pendentes no mapa (visíveis, não vencidas):")
        for a in pend:
            print(f"    área {a['i']:>2}  hex {a['i']//7:>2} pos {a['i']%7}  "
                  f"{a['crew']:<24} flags 0x{a['flags']:02X} ({flag_names(a['flags'])})")
        print("\nO jogo só grava a tentativa (flag 0x01) quando a CORRIDA TERMINA.")
        print("=> rode a missão até o fim (vencer OU perder), volte ao mapa,")
        print("   salve no menu e feche o jogo. Sair no meio não marca nada.")
        return 1
    print(f"áreas que mudaram: {len(mudou)}\n")
    for i in mudou:
        x, y = aa[i], ab[i]
        print(f"  área {i:>2}  [{y['crew']}]")
        print(f"      antes: owner_a=0x{x['owner_a']:08X} owner_b=0x{x['owner_b']:08X} "
              f"flags=0x{x['flags']:02X} ({flag_names(x['flags'])})")
        print(f"      agora: owner_a=0x{y['owner_a']:08X} owner_b=0x{y['owner_b']:08X} "
              f"flags=0x{y['flags']:02X} ({flag_names(y['flags'])})")
        if not x["tried"] and y["tried"]:
            print("      -> TENTADA agora (você entrou nesta missão)")
        if not x["won"] and y["won"]:
            print("      -> VENCIDA agora")
    # marcador secundário: a tabela de eventos (0x10050) também muda
    ev = []
    for s in range(243):
        off = F.EVENT_OFFSET + s * F.EVENT_STRIDE
        if a[off:off + 16] != b[off:off + 16]:
            ev.append(s)
    if ev:
        print(f"\nslots de evento que mudaram: {ev}")
    return 0


def cmd_marcar(src: str, dst: str, indices, forcar: bool) -> int:
    data = bytearray(open(src, "rb").read())
    changed = []
    for i in indices:
        if not 0 <= i < F.AREA_COUNT:
            print(f"área {i}: fora da faixa (0..{F.AREA_COUNT-1})", file=sys.stderr)
            continue
        owner_a, owner_b, flags = F.area_record(data, i)
        if not flags & 0x40 and not forcar:
            print(f"área {i}: NÃO visível no mapa (flags=0x{flags:02X}) — pulando. "
                  f"Use --forcar se for isso mesmo.", file=sys.stderr)
            continue
        if flags & 0x02:
            print(f"área {i}: já vencida — pulando", file=sys.stderr)
            continue
        # Mesmo formato que o JOGO grava ao vencer (evidência: PROBE_K -> PROBE_L,
        # área 48: 40693BA3/00000000/00 -> C49D46EF/C49D46EF/42; e a área 61 do
        # save real, 0x43 = visível|tentada|vencida):
        #   owner_a = JOGADOR
        #   owner_b = o crew do outro lado; se era 0, o jogo grava JOGADOR
        #   flags   = visível | vencida, preservando "tentada" e "rival"
        novo_owner_b = owner_b if owner_b else F.PLAYER
        novo_flags = (flags & KEEP_FLAGS) | WON | 0x40
        F.put32(data, F.AREA_OFFSET + i * F.AREA_STRIDE, F.PLAYER)
        F.put32(data, F.AREA_OFFSET + i * F.AREA_STRIDE + 4, novo_owner_b)
        data[F.AREA_OFFSET + i * F.AREA_STRIDE + 8] = novo_flags
        changed.append(i)
        print(f"  área {i:>2}: {owner_a:08X}/{owner_b:08X}/{flags:02X} -> "
              f"{F.PLAYER:08X}/{novo_owner_b:08X}/{novo_flags:02X}  "
              f"({describe(owner_a)}, hex {i//7}, pos {i%7})")
    if not changed:
        print("nada alterado")
        return 1
    digest = F.update_profile_md5(data)
    F.update_hashes(data)
    open(dst, "wb").write(data)
    print(f"\nMD5 do perfil -> {digest.hex()}")
    print("CRC da EA recalculados (0x10/0x14/0x18)")
    print(f"save gravado: {dst}")
    return 0


def load_map() -> dict:
    if not os.path.isfile(MAP_FILE):
        return {}
    with open(MAP_FILE, "r", encoding="utf-8") as fh:
        return json.load(fh)


def save_map(data: dict) -> None:
    with open(MAP_FILE, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2, ensure_ascii=False, sort_keys=True)
        fh.write("\n")


def cmd_mapear(nome: str, indice: int) -> int:
    m = load_map()
    m[nome] = indice
    save_map(m)
    print(f"'{nome}' -> área {indice}")
    return 0


def cmd_resolver(path: str, nome: str) -> int:
    m = load_map()
    if nome not in m:
        print(f"'{nome}' ainda não foi identificado. Identifique com 'diff' e registre com 'mapear'.",
              file=sys.stderr)
        return 1
    i = m[nome]
    owner_a, owner_b, flags = F.area_record(open(path, "rb").read(), i)
    print(f"'{nome}' = área {i}  hex {i//7} pos {i%7}  crew {describe(owner_a)}  flags 0x{flags:02X}")
    return 0


def cmd_puxar(destino: str) -> int:
    subprocess.run(["adb", "pull", REMOTE, destino], check=True)
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)

    p = sub.add_parser("mapa", help="padrões por hexágono")
    p.add_argument("save")

    p = sub.add_parser("areas", help="lista as 73 áreas")
    p.add_argument("save")

    p = sub.add_parser("diff", help="o que mudou (identificação das missões)")
    p.add_argument("antes")
    p.add_argument("depois")

    p = sub.add_parser("marcar", help="forja a vitória")
    p.add_argument("save")
    p.add_argument("out")
    p.add_argument("areas", nargs="+", type=int)
    p.add_argument("--forcar", action="store_true", help="força área não visível")

    p = sub.add_parser("resolver", help="nome -> índice")
    p.add_argument("save")
    p.add_argument("nome")

    p = sub.add_parser("mapear", help="registra nome -> índice")
    p.add_argument("nome")
    p.add_argument("indice", type=int)

    p = sub.add_parser("puxar", help="baixa o save exportado do aparelho")
    p.add_argument("destino")

    args = ap.parse_args()
    if args.cmd == "mapa":
        return cmd_mapa(args.save)
    if args.cmd == "areas":
        return cmd_areas(args.save)
    if args.cmd == "diff":
        return cmd_diff(args.antes, args.depois)
    if args.cmd == "marcar":
        return cmd_marcar(args.save, args.out, args.areas, args.forcar)
    if args.cmd == "resolver":
        return cmd_resolver(args.save, args.nome)
    if args.cmd == "mapear":
        return cmd_mapear(args.nome, args.indice)
    if args.cmd == "puxar":
        return cmd_puxar(args.destino)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
