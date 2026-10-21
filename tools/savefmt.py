#!/usr/bin/env python3
"""Dementech · NFS Carbon — biblioteca do formato do save (porta do editor nfsed).

Reúne o que já foi provado na reversing do save do Carbon:

  * container MC02 big-endian e a cadeia de blocos JDLZ/HUFF;
  * JDLZ (decompress + compress) — o round-trip é byte a byte;
  * tabela de áreas (0x4DC, 73 registros x 12 B): owner_a, owner_b, flags;
        0x40 = visível · 0x01 = tentada · 0x02 = vencida · 0x20 = do jogador
        0x04 = rival · 0x66 = estado que o jogo grava quando o jogador vence;
  * tabela de eventos (0x10050, stride 16): [hash u32][flags u32][scoreA][scoreB];
  * MD5 do perfil: md5(d[0x4C : 0x4C+0xFFF0]) gravado em 0x3C;
  * 3 CRC da EA (tabela customizada): 0x10, 0x14, 0x18.

SPDX-License-Identifier: MIT
"""
from __future__ import annotations

import hashlib
import struct

MC02_MAGIC = 0x4D433032
JDLZ_MAGIC = 0x4A444C5A
HUFF_MAGIC = 0x48554646
CHAIN_MAGIC = 0x32313435

AREA_OFFSET = 0x4DC
AREA_COUNT = 73
AREA_STRIDE = 12
EVENT_OFFSET = 0x10050
EVENT_STRIDE = 16
PROFILE_START = 0x4C
PROFILE_MD5_SPAN = 0xFFF0
PLAYER = 0xC49D46EF
WON_FLAGS = 0x66

# CRC da EA: polinômio 0x04C11DB7, tabela MSB-first, seed invertida.
def _build_crc_table():
    table = []
    for i in range(256):
        crc = i << 24
        for _ in range(8):
            crc = ((crc << 1) ^ 0x04C11DB7) & 0xFFFFFFFF if crc & 0x80000000 else (crc << 1) & 0xFFFFFFFF
        table.append(crc)
    return table


_CRC_TABLE = _build_crc_table()


def be32(data: bytes, offset: int) -> int:
    return struct.unpack_from(">I", data, offset)[0]


def put32(data: bytearray, offset: int, value: int) -> None:
    struct.pack_into(">I", data, offset, value & 0xFFFFFFFF)


def ea_crc(data: bytes, offset: int, length: int) -> int:
    """CRC32 da EA (o do editor nfsed, tabela MSB-first, seed invertida)."""
    if length < 4:
        return 0
    crc = (~be32(data, offset)) & 0xFFFFFFFF
    for i in range(offset + 4, offset + length):
        crc = _CRC_TABLE[crc >> 24] ^ ((crc << 8) | data[i]) & 0xFFFFFFFF
    return (~crc) & 0xFFFFFFFF


def update_hashes(data: bytearray) -> None:
    """Recalcula os 3 CRC da EA (0x10, 0x14, 0x18)."""
    len1 = be32(data, 0x08)
    len2 = be32(data, 0x0C)
    put32(data, 0x10, ea_crc(data, 0x1C, len1))
    put32(data, 0x14, ea_crc(data, 0x1C + len1, len2))
    put32(data, 0x18, ea_crc(data, 0x00, 0x18))


def update_profile_md5(data: bytearray) -> bytes:
    """Recalcula o MD5 do perfil (0x3C) — obrigatório após mexer em 0x4C..0x1003C."""
    digest = hashlib.md5(bytes(data[PROFILE_START:PROFILE_START + PROFILE_MD5_SPAN])).digest()
    data[0x3C:0x4C] = digest
    return digest


def area_record(data: bytes, index: int):
    offset = AREA_OFFSET + index * AREA_STRIDE
    owner_a = be32(data, offset)
    owner_b = be32(data, offset + 4)
    flags = data[offset + 8]
    return owner_a, owner_b, flags


def set_area_won(data: bytearray, index: int, flags: int = WON_FLAGS) -> tuple:
    """Marca a corrida como vencida pelo jogador (o que mostra o ✓ no mapa)."""
    offset = AREA_OFFSET + index * AREA_STRIDE
    before = area_record(data, index)
    put32(data, offset, PLAYER)
    put32(data, offset + 4, PLAYER)
    data[offset + 8] = flags
    return before


def walk_containers(data: bytes, pos: int):
    """Itera os containers da cadeia (yield offset, dec_size, cont_len, target)."""
    cur = pos
    while cur + 0x18 <= len(data) and be32(data, cur) == CHAIN_MAGIC:
        yield cur, be32(data, cur + 0x04), be32(data, cur + 0x08), be32(data, cur + 0x0C)
        nxt = cur + be32(data, cur + 0x08)
        if nxt <= cur:
            return
        cur = nxt


def profile_blocks(data: bytes):
    """Blocos JDLZ do perfil: (offset, bytes descomprimidos)."""
    pos = be32(data, 0x20) + 0x2C
    for cur, dec_size, cont_len, target in walk_containers(data, pos):
        payload = data[cur + 0x18:cur + cont_len]
        if be32(payload, 0) == JDLZ_MAGIC:
            yield cur, payload
