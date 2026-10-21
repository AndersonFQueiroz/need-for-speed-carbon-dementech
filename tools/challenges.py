#!/usr/bin/env python3
"""Dementech · NFS Carbon — desafios (challenge series) do save.

Porta do editor C# (nfsed / "NFS Carbon/Read_Data_Table.cs" e
"Helper Functions.cs"):

  * VLT_Hash  -> Jenkins lookup3 32-bit (Mix32) com a semente do jogo;
  * Grab_Position_From_Anchor -> acha o bloco de carreira (âncora 0x033FA23A);
  * Unlock_All_Challenge_Series -> marca um desafio com OR 4 no u32 seguinte
    ao hash do rótulo.

Uso:
    python3 tools/challenges.py list  <save>              # estado dos 45 desafios
    python3 tools/challenges.py patch <save> <out> <rotulo> [rotulo ...]
    python3 tools/challenges.py scan  <save>              # entradas desconhecidas
"""
from __future__ import annotations

import argparse
import struct
import sys

MASK = 0xFFFFFFFF
CHALLENGE_ARRAY = [
    "cs.1.1", "cs.1.2", "cs.1.3", "cs.2.1", "cs.2.2", "cs.2.3", "cs.3.1", "cs.3.2", "cs.3.3",
    "cs.13.1", "cs.13.2", "cs.13.3", "cs.14.4", "cs.14.2", "cs.14.3", "cs.6.1", "cs.6.2", "cs.6.3",
    "cs.7.1", "cs.7.2", "cs.7.3", "cs.8.1", "cs.8.2", "cs.8.3", "cs.9.1", "cs.9.2", "cs.9.3",
    "cs.10.1", "cs.10.2", "cs.10.3", "cs.11.1", "cs.11.2", "cs.11.3", "cs.12.1", "cs.12.2", "cs.12.3",
    "ce.4.1", "ce.4.3", "ce.4.2", "ce.1.1", "ce.1.2", "ce.1.3", "ce.1.4", "ce.1.5", "ce.1.6",
    "ce.5.1", "ce.5.2", "ce.3.1", "ce.15.2", "ce.15.3",
]
CAREER_ANCHOR = 0x033FA23A
ACHIEVED_BIT = 4


# ------------------------------------------------------------------ VLT_Hash
def _rot(x, n):
    return ((x << n) | (x >> (32 - n))) & MASK


def _mix32_1(a, b, c):
    a = ((c >> 13) ^ ((a - b - c) & MASK)) & MASK
    b = ((a << 8) ^ ((b - c - a) & MASK)) & MASK
    c = ((b >> 13) ^ ((c - a - b) & MASK)) & MASK
    a = ((c >> 12) ^ ((a - b - c) & MASK)) & MASK
    b = ((a << 16) ^ ((b - c - a) & MASK)) & MASK
    c = ((b >> 5) ^ ((c - a - b) & MASK)) & MASK
    a = ((c >> 3) ^ ((a - b - c) & MASK)) & MASK
    b = ((a << 10) ^ ((b - c - a) & MASK)) & MASK
    c = ((b >> 15) ^ ((c - a - b) & MASK)) & MASK
    return a, b, c


def _mix32_2(a, b, c):
    a = ((c >> 13) ^ ((a - b - c) & MASK)) & MASK
    b = ((a << 8) ^ ((b - c - a) & MASK)) & MASK
    c = ((b >> 13) ^ ((c - a - b) & MASK)) & MASK
    a = ((c >> 12) ^ ((a - b - c) & MASK)) & MASK
    b = ((a << 16) ^ ((b - c - a) & MASK)) & MASK
    c = ((b >> 5) ^ ((c - a - b) & MASK)) & MASK
    a = ((c >> 3) ^ ((a - b - c) & MASK)) & MASK
    b = ((a << 10) ^ ((b - c - a) & MASK)) & MASK
    return ((b >> 15) ^ ((c - a - b) & MASK)) & MASK


def vlt_hash(value: str) -> int:
    """Jenkins lookup3 32-bit com a semente do jogo (VLT_Hash)."""
    if not value or value.isspace():
        return 0
    arr = value.encode("ascii", "ignore")
    a, b, c = 0x9E3779B9, 0x9E3779B9, 0xABCDEF00
    v1, v2 = 0, len(arr)
    while v2 >= 12:
        a = (a + int.from_bytes(arr[v1:v1 + 4], "little")) & MASK
        b = (b + int.from_bytes(arr[v1 + 4:v1 + 8], "little")) & MASK
        c = (c + int.from_bytes(arr[v1 + 8:v1 + 12], "little")) & MASK
        a, b, c = _mix32_1(a, b, c)
        v1 += 12
        v2 -= 12
    c = (c + len(arr)) & MASK
    if v2 >= 11:
        c = (c + (arr[v1 + 10] << 24)) & MASK
    if v2 >= 10:
        c = (c + (arr[v1 + 9] << 16)) & MASK
    if v2 >= 9:
        c = (c + (arr[v1 + 8] << 8)) & MASK
    if v2 >= 8:
        b = (b + (arr[v1 + 7] << 24)) & MASK
    if v2 >= 7:
        b = (b + (arr[v1 + 6] << 16)) & MASK
    if v2 >= 6:
        b = (b + (arr[v1 + 5] << 8)) & MASK
    if v2 >= 5:
        b = (b + arr[v1 + 4]) & MASK
    if v2 >= 4:
        a = (a + (arr[v1 + 3] << 24)) & MASK
    if v2 >= 3:
        a = (a + (arr[v1 + 2] << 16)) & MASK
    if v2 >= 2:
        a = (a + (arr[v1 + 1] << 8)) & MASK
    if v2 >= 1:
        a = (a + arr[v1]) & MASK
    return _mix32_2(a, b, c)


# --------------------------------------------------------------------- save
def find_game(data: bytes) -> int:
    """MD5_Prep.Find_Game: procura 'emaG' ou 'Game'."""
    for i in range(len(data) - 4):
        chunk = data[i:i + 4]
        if chunk == b"emaG" or chunk == b"Game":
            return i
    return 0


def career_block(data: bytes) -> int:
    """Grab_Position_From_Anchor: âncora 0x033FA23A a passo 0x10."""
    pos = find_game(data)
    while pos + 4 <= len(data):
        if struct.unpack_from(">I", data, pos)[0] == CAREER_ANCHOR:
            return pos
        pos += 0x10
    return -1


def find_challenge(data: bytes, label: str, start: int) -> int:
    """Posição do hash do rótulo dentro do bloco de carreira (a passo 4)."""
    target = vlt_hash(label)
    pos = start
    while pos + 8 <= len(data):
        if struct.unpack_from(">I", data, pos)[0] == target:
            return pos
        pos += 4
    return -1


def challenge_state(data: bytes, label: str, start: int):
    pos = find_challenge(data, label, start)
    if pos < 0:
        return -1, None
    value = struct.unpack_from(">I", data, pos + 4)[0]
    return pos, value


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    p_list = sub.add_parser("list", help="estado dos desafios")
    p_list.add_argument("save")
    p_scan = sub.add_parser("scan", help="entradas do bloco de carreira")
    p_scan.add_argument("save")
    p_patch = sub.add_parser("patch", help="marca desafios como concluidos")
    p_patch.add_argument("save")
    p_patch.add_argument("out")
    p_patch.add_argument("labels", nargs="+")
    args = ap.parse_args()

    data = bytearray(open(args.save, "rb").read())
    start = career_block(data)
    if start < 0:
        print("ancora do bloco de carreira nao encontrada", file=sys.stderr)
        return 1

    if args.cmd == "list":
        print(f"bloco de carreira em 0x{start:X}")
        done = pending = missing = 0
        for label in CHALLENGE_ARRAY:
            pos, value = challenge_state(data, label, start)
            if pos < 0:
                print(f"  {label:<9} NAO ENCONTRADO")
                missing += 1
                continue
            flag = "OK " if (value & ACHIEVED_BIT) else "-- "
            print(f"  {label:<9} {flag} pos=0x{pos:05X} valor={value} (0x{value:08X})")
            done += 1 if (value & ACHIEVED_BIT) else 0
            pending += 0 if (value & ACHIEVED_BIT) else 1
        print(f"\nconcluidos: {done} | pendentes: {pending} | nao encontrados: {missing}")
        return 0

    if args.cmd == "scan":
        known = {vlt_hash(l): l for l in CHALLENGE_ARRAY}
        pos = start
        count = 0
        while pos + 8 <= len(data):
            h = struct.unpack_from(">I", data, pos)[0]
            value = struct.unpack_from(">I", data, pos + 4)[0]
            if h in known:
                count += 1
            pos += 4
        print(f"entradas conhecidas: {count}")
        return 0

    # patch
    changed = []
    for label in args.labels:
        pos, value = challenge_state(data, label, start)
        if pos < 0:
            print(f"{label}: NAO ENCONTRADO no save (nada alterado)", file=sys.stderr)
            continue
        if value & ACHIEVED_BIT:
            print(f"{label}: ja estava concluído (valor 0x{value:08X})")
            continue
        new = value | ACHIEVED_BIT
        struct.pack_into(">I", data, pos + 4, new)
        changed.append((label, pos, value, new))
        print(f"{label}: 0x{value:08X} -> 0x{new:08X} (pos 0x{pos:05X})")
    if not changed:
        print("nada alterado")
        return 0
    open(args.out, "wb").write(data)
    print(f"save alterado gravado em {args.out} ({len(changed)} desafios)")
    print("ATENCAO: ainda falta recalcular o MD5 do perfil e os 3 CRC da EA "
          "(use setcash.py / update_hashes) antes de entregar ao jogo")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
