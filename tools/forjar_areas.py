#!/usr/bin/env python3
"""Dementech · NFS Carbon — forja corridas vencidas na tabela de áreas.

Marca a corrida como vencida pelo jogador (aparece o ✓ no mapa e o card vira
"UNLOCKS ACHIEVED"), recalcula o MD5 do perfil e os 3 CRC da EA.

    python3 tools/forjar_areas.py <save> <out> <area> [area ...]
    python3 tools/forjar_areas.py --list <save>
"""
from __future__ import annotations

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import savefmt as F  # noqa: E402

NAMES = {
    0x40693BA3: "KENJI", 0xA9382979: "WOLF", 0x27182028: "ANGIE",
    0x10A2F397: "DARIUS", 0x97F3A210: "?5", 0xC49D46EF: "JOGADOR",
    0xCF59E641: "?6", 0x3400C6F4: "?7", 0x1AB75EC8: "?8",
}


def describe(owner: int) -> str:
    return NAMES.get(owner, f"0x{owner:08X}")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--list", action="store_true", help="lista as 73 areas")
    ap.add_argument("save")
    ap.add_argument("out", nargs="?")
    ap.add_argument("areas", nargs="*", type=int)
    args = ap.parse_args()

    data = bytearray(open(args.save, "rb").read())

    if args.list:
        print(f'{"idx":>3} {"owner_a":>10} {"owner_b":>10} {"fl":>3}  estado')
        for i in range(F.AREA_COUNT):
            a, b, f = F.area_record(data, i)
            won = bool(f & 0x02)
            state = "VENCIDA" if won else ("rival" if a != F.PLAYER else "livre")
            print(f"{i:>3} {describe(a):>10} {describe(b):>10} {f:>3}  {state}")
        return 0

    if not args.out or not args.areas:
        ap.error("informe <out> e pelo menos uma area")

    changed = []
    for index in args.areas:
        if not 0 <= index < F.AREA_COUNT:
            print(f"area {index}: fora da faixa (0..{F.AREA_COUNT-1})", file=sys.stderr)
            continue
        before = F.set_area_won(data, index)
        after = F.area_record(data, index)
        changed.append(index)
        print(f"area {index:>2}: ({describe(before[0])},{describe(before[1])},{before[2]:02X})"
              f" -> ({describe(after[0])},{describe(after[1])},{after[2]:02X})")

    if not changed:
        print("nada alterado")
        return 1

    digest = F.update_profile_md5(data)
    F.update_hashes(data)
    open(args.out, "wb").write(data)
    print(f"\nMD5 do perfil -> {digest.hex()}")
    print(f"CRC da EA recalculados (0x10/0x14/0x18)")
    print(f"save gravado: {args.out} ({len(data)} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
