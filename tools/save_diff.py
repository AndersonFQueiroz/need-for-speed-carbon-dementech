#!/usr/bin/env python3
"""Dementech · NFS Carbon — diff entre dois saves.

Mostra o que mudou byte a byte, com foco nas estruturas que interessam
(tabela de áreas em 0x4DC e tabela de eventos em 0x10050).

    python3 tools/save_diff.py <save_antigo> <save_novo> [--all]
"""
from __future__ import annotations

import argparse
import struct

AREA_OFF = 0x4DC
AREA_COUNT = 73
AREA_STRIDE = 12
EVENT_OFF = 0x10050
EVENT_STRIDE = 16
PLAYER = 0xC49D46EF


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("old")
    ap.add_argument("new")
    ap.add_argument("--all", action="store_true", help="lista toda mudanca de byte")
    args = ap.parse_args()

    a = open(args.old, "rb").read()
    b = open(args.new, "rb").read()
    if len(a) != len(b):
        print(f"!! tamanhos diferentes: {len(a)} vs {len(b)}")
        return 1

    # --- bytes mudados
    diffs = [i for i in range(len(a)) if a[i] != b[i]]
    print(f"bytes alterados: {len(diffs)}")

    # --- tabela de areas
    print("\n=== areas (0x4DC, 73 registros) ===")
    for i in range(AREA_COUNT):
        o = AREA_OFF + i * AREA_STRIDE
        ao, bo = a[o:o + AREA_STRIDE], b[o:o + AREA_STRIDE]
        if ao == bo:
            continue
        aa, ab = struct.unpack_from(">II", a, o)
        ba, bb = struct.unpack_from(">II", b, o)
        print(f"  area {i:>2}: ({aa:08X},{ab:08X},{a[o+8]:02X}) -> ({ba:08X},{bb:08X},{b[o+8]:02X})")

    # --- tabela de eventos
    print("\n=== eventos (0x10050) ===")
    slot = 0
    while EVENT_OFF + slot * EVENT_STRIDE + EVENT_STRIDE <= len(a):
        o = EVENT_OFF + slot * EVENT_STRIDE
        if a[o:o + EVENT_STRIDE] != b[o:o + EVENT_STRIDE]:
            ae, af, aa, ab = struct.unpack_from(">IIII", a, o)
            be, bf, ba, bb = struct.unpack_from(">IIII", b, o)
            print(f"  slot {slot:>3}: ev {ae:08X} flags {af:02X} A={aa} B={ab}"
                  f"  ->  ev {be:08X} flags {bf:02X} A={ba} B={bb}")
        slot += 1

    if args.all:
        print("\n=== todos os bytes ===")
        for i in diffs[:2000]:
            print(f"  0x{i:05X}: {a[i]:02X} -> {b[i]:02X}")
    else:
        print("\n( use --all para ver os bytes )")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
