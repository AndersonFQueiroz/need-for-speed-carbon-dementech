"""Set career cash in an NFS Carbon (Xbox 360, MC02) profile save.

Layout discovered by reversing TheLastOfLogic/NFS-Carbon-Save-Editor plus the
real file:

  MC02 header
    0x00 'MC02'
    0x04 total file length
    0x08 len1   (0x10)
    0x0C len2   (0x46000)
    0x10 cs1    CRC32 of [0x1C, 0x1C+len1)
    0x14 cs2    CRC32 of [0x1C+len1, +len2)   <- covers the whole profile chain
    0x18 cs3    CRC32 of [0x00, 0x18)
    0x1C chain length
    0x20 chain-relative offset; Pos = 0x20 value + 0x2C

  At Pos: a chain of 22114455 containers (magic 55 44 11 22), each 0x18 bytes
  of header + a compressed chunk. Chunk N is stored at Decompressed offset
  targetOffset (0x8000 apart). Most chunks are JDLZ; the very first one is
  HUFF. Only the container that actually holds the money field is rewritten,
  every other container is copied byte for byte.
"""
import sys

CHUNK = 0x8000
MONEY_LABEL = b'2287685084:'
MONEY_TAB = 7
MAGIC = 0x55441122
HUFF_MAGIC = 0x46465548
JDLZ_MAGIC = 0x5A4C444A


def be32(d, o):
    return int.from_bytes(d[o:o + 4], 'big')


def put32(d, o, v):
    d[o:o + 4] = v.to_bytes(4, 'big')


# ---------------------------------------------------------------- JDLZ decode
def jdlz_decompress(buf, inpos=0):
    assert buf[inpos:inpos + 4] in (b'ZLDJ', b'JDLZ'), buf[inpos:inpos + 4]
    out_len = be32(buf, inpos + 8)
    out = bytearray(out_len)
    ip = inpos + 16
    op = 0
    flags1 = flags2 = 1
    while ip < len(buf) and op < out_len:
        if flags1 == 1:
            flags1 = buf[ip] | 0x100
            ip += 1
        if flags2 == 1:
            flags2 = buf[ip] | 0x100
            ip += 1
        if flags1 & 1:
            b0, b1 = buf[ip], buf[ip + 1]
            ip += 2
            if flags2 & 1:
                length = (b1 | ((b0 & 0xF0) << 4)) + 3
                dist = (b0 & 0x0F) + 1
            else:
                dist = (b1 | ((b0 & 0xE0) << 3)) + 17
                length = (b0 & 0x1F) + 3
            for _ in range(length):
                out[op] = out[op - dist]
                op += 1
            flags2 >>= 1
        else:
            out[op] = buf[ip]
            op += 1
            ip += 1
        flags1 >>= 1
    if op != out_len:
        raise ValueError('length mismatch %d != %d' % (op, out_len))
    return bytes(out)


# ---------------------------------------------------------------- JDLZ encode
def jdlz_compress(data):
    """Greedy LZSS producing a stream the EA JDLZ decoder accepts.

    Two independent bit streams: flags1 picks literal vs back-reference,
    flags2 picks the back-reference encoding. Both are big-endian-first bits
    from the low bit up, reloaded from the input whenever only the sentinel
    bit is left.
    """
    win, min_match, max_match = 0x810, 3, 0x1002
    tokens = []                                  # ('l', byte) | ('r', dist, len)
    i = 0
    n = len(data)
    head = {}
    while i < n:
        best_len = best_dist = 0
        if i + min_match <= n:
            key = data[i:i + min_match]
            for cand in head.get(key, ()):
                dist = i - cand
                if dist > win:
                    continue
                ln = min_match
                while ln < max_match and i + ln < n and data[cand + ln] == data[i + ln]:
                    ln += 1
                if ln > best_len:
                    best_len, best_dist = ln, dist
                    if ln == max_match:
                        break
            head.setdefault(key, []).append(i)
            if len(head[key]) > 64:
                del head[key][:-64]
        if best_len >= min_match:
            # A back-reference with distance > 16 can only encode length 3..34
            # (5 bits); short distances go up to 0x1002 (12 bits).
            rem = best_len
            if best_dist > 16:
                while rem > 34:
                    tokens.append(('r', best_dist, 34))
                    rem -= 34
                if rem < 3:            # too short to encode -> emit as literals
                    for k in range(rem):
                        tokens.append(('l', data[i + best_len - rem + k]))
                    rem = 0
            if rem:
                tokens.append(('r', best_dist, rem))
            step = best_len
        else:
            tokens.append(('l', data[i]))
            step = 1
        i += step

    # Serialise. The decoder interleaves flag-byte reloads with data reads in a
    # strict order, and a flag byte's bits are only known after the next 8
    # tokens -- so first lay out the byte stream, then patch the flag slots.
    # JDLZ header is 16 bytes: magic, version, unpacked len, packed len.
    out = bytearray(b'ZLDJ' + b'\x02\x10\x00\x00'
                    + len(data).to_bytes(4, 'big') + b'\x00\x00\x00\x00')
    f1 = f2 = 1                       # sentinel only -> "reload me"
    slot1 = slot2 = -1                # index of the pending flag byte
    f1b = f2b = 0                     # bits of the pending flag byte
    n1 = n2 = 0                       # how many of its 8 bits are filled

    for tok in tokens:
        if f1 == 1:                   # decoder reloads flags1 then flags2
            out.append(0)
            slot1, f1, f1b, n1 = len(out) - 1, 0x100, 0, 0
        if f2 == 1:
            out.append(0)
            slot2, f2, f2b, n2 = len(out) - 1, 0x100, 0, 0

        if tok[0] == 'l':
            out[slot1] = f1b          # flags1 bit clear -> literal
            n1 += 1
            f1 >>= 1
            out.append(tok[1])
        else:
            _, dist, length = tok
            f1b |= 1 << n1             # flags1 bit set -> back-reference
            out[slot1] = f1b
            n1 += 1
            f1 >>= 1
            if dist <= 16:             # short distance, long length
                # length-3: low 8 bits -> b1, high 4 bits -> b0 bits 4..7
                b0 = (dist - 1) | ((((length - 3) >> 8) & 0x0F) << 4)
                b1 = (length - 3) & 0xFF
            else:                     # long distance, short length
                # dist-17: low 8 bits -> b1, high 3 bits -> b0 bits 5..7
                b0 = (length - 3) | ((((dist - 17) >> 8) & 0x07) << 5)
                b1 = (dist - 17) & 0xFF
            out.append(b0)
            out.append(b1)
            if dist <= 16:
                f2b |= 1 << n2
            out[slot2] = f2b
            n2 += 1
            f2 >>= 1
    out[0xC:0x10] = len(out).to_bytes(4, 'big')
    return bytes(out)


# -------------------------------------------------------------------- CRC32
_CRC_TABLE = []
for _i in range(256):
    _c = _i << 24
    for _ in range(8):
        _c = ((_c << 1) ^ 0x04C11DB7) if (_c & 0x80000000) else (_c << 1)
    _CRC_TABLE.append(_c & 0xFFFFFFFF)


def ea_crc(data, offset, length):
    """Matches EA CRC32.InternalCompute: BE seed, 4 bytes skipped, bitwise table."""
    if length < 4:
        return 0
    crc = (~be32(data, offset)) & 0xFFFFFFFF
    for i in range(offset + 4, offset + length):
        crc = _CRC_TABLE[crc >> 24] ^ ((crc << 8) | data[i]) & 0xFFFFFFFF
    return (~crc) & 0xFFFFFFFF


def update_hashes(d):
    len1 = be32(d, 0x08)
    len2 = be32(d, 0x0C)
    put32(d, 0x10, ea_crc(d, 0x1C, len1))
    put32(d, 0x14, ea_crc(d, 0x1C + len1, len2))
    put32(d, 0x18, ea_crc(d, 0x00, 0x18))


# ------------------------------------------------------------------- container
def walk(d, pos):
    """Yield (index, container_offset, header_fields) for each container."""
    cur = pos
    while cur + 0x18 <= len(d) and be32(d, cur) == MAGIC:
        yield (cur, be32(d, cur + 0x04), be32(d, cur + 0x08),
               be32(d, cur + 0x0C), be32(d, cur + 0x10))
        nxt = cur + be32(d, cur + 0x08)
        if nxt <= cur:
            return
        cur = nxt


def read_money(d, verbose=True):
    """Return (cash_str, container_offset, decompressed_offset)."""
    pos = be32(d, 0x20) + 0x2C
    for cur, dec_size, cont_len, target, _prev in walk(d, pos):
        payload = d[cur + 0x18:cur + cont_len]
        if be32(payload, 0) != JDLZ_MAGIC:
            if verbose:
                print('  @0x%X HUFF block (%d B) left untouched' % (cur, dec_size))
            continue
        blob = jdlz_decompress(payload)
        idx = blob.find(MONEY_LABEL)
        if idx < 0:
            if verbose:
                print('  @0x%X JDLZ -> %d B, no money field' % (cur, len(blob)))
            continue
        start = blob.rfind(b'\n', 0, idx) + 1
        end = blob.find(b'\n', idx)
        line = blob[start:end]
        tabs = line.split(b'\t')
        if verbose:
            print('  @0x%X JDLZ -> %d B  money=%s (tab %d of %d)'
                  % (cur, len(blob), tabs[MONEY_TAB].decode(), MONEY_TAB, len(tabs)))
        return tabs[MONEY_TAB].decode(), cur, idx, blob
    return None, None, None, None


def set_cash(src, dst, value, verbose=True):
    d = bytearray(open(src, 'rb').read())
    orig_len = len(d)
    pos = be32(d, 0x20) + 0x2C

    cash, cur, idx, blob = read_money(d, verbose)
    if cur is None:
        raise SystemExit('money field not found')

    # patch the field inside the decompressed chunk
    start = blob.rfind(b'\n', 0, idx) + 1
    end = blob.find(b'\n', idx)
    tabs = blob[start:end].split(b'\t')
    old = tabs[MONEY_TAB]
    new = str(value).encode()
    tabs[MONEY_TAB] = new
    blob = blob[:start] + b'\t'.join(tabs) + blob[end:]

    # A chunk must keep its exact decompressed size, otherwise it would run
    # into the next chunk's target offset. The chunk is NUL-padded at the end,
    # so any growth is absorbed from there.
    chunk_len = be32(d, cur + 0x04)
    if len(blob) > chunk_len:
        excess = len(blob) - chunk_len
        if blob[-excess:] != b'\x00' * excess:
            raise SystemExit('grew by %d B but chunk tail is not NUL padding'
                             % excess)
        blob = blob[:-excess]
        if verbose:
            print('  absorbed %d B of NUL padding (chunk stays %d B)'
                  % (excess, chunk_len))
    elif len(blob) < chunk_len:
        blob += b'\x00' * (chunk_len - len(blob))

    # recompress just this one chunk and rebuild the chain in place
    packed = jdlz_compress(blob)
    pad = (-len(packed)) % 4
    packed += b'\x00' * pad
    old_len = be32(d, cur + 0x08)
    new_len = 0x18 + len(packed)
    delta = new_len - old_len
    if verbose:
        print('  chunk %d -> %d B  (container %d -> %d, delta %+d)'
              % (len(blob), len(packed), old_len, new_len, delta))

    if delta:
        put32(d, cur + 0x08, new_len)
        d[cur + 0x18:cur + old_len] = packed + b'\x00' * (old_len - new_len)
        tail = d[cur + old_len:]
        del d[cur + new_len:]
        d += tail
        # container offsets moved: re-walk and fix the cumulative "prev" field
        acc = 0
        for c, _ds, cl, _t, _p in walk(d, pos):
            put32(d, c + 0x10, acc)
            acc += cl
        put32(d, 0x1C, acc)
    else:
        d[cur + 0x18:cur + 0x18 + len(packed)] = packed

    # the save has a fixed total size: take the growth back out of the zero pad
    if len(d) != orig_len:
        if len(d) > orig_len:
            if set(d[orig_len:]) != {0}:
                raise SystemExit('file grew and the tail is not zero padding')
            del d[orig_len:]
        else:
            d += b'\x00' * (orig_len - len(d))

    update_hashes(d)
    open(dst, 'wb').write(d)
    if verbose:
        print('  cash %s -> %s' % (old.decode(), new.decode()))
        print('  wrote %s (%d bytes, chain 0x%X)'
              % (dst, len(d), be32(d, 0x1C)))


if __name__ == '__main__':
    src = sys.argv[1]
    value = sys.argv[2]
    dst = sys.argv[3] if len(sys.argv) > 3 else src + '.patched'
    set_cash(src, dst, value)
