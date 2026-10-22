#!/usr/bin/env python3
"""Dementech · NFS Carbon — patch do motor (librexruntime.so).

O presenter Vulkan do ReXGlue (SDK público `rexglue/rexglue-sdk`, BSD-3) trata
`VK_ERROR_DEVICE_LOST` de forma diferente em cada caminho:

  * aquisição da imagem  -> `PaintResult::kGpuLostResponsible`  (ok)
  * apresentação        -> `PaintResult::kGpuLostResponsible`  (ok)
  * submissão (submit) -> `PaintResult::kNotPresentedConnectionOutdated`  (BUG)

No caminho do submit, a perda do device é tratada como "a surface ficou velha":
o presenter fica recriando a swapchain sem parar (190 recriações em 5 s no
aparelho do dono) e, no fim, `Failed to create a Vulkan surface` — a tela fica
preta para sempre e a CPU fica em 100 %.

O patch troca o retorno desse caminho por `kGpuLostResponsible` (5): o presenter
para de girar e o `Presenter` chama o `host_gpu_loss_callback_` do host — que
pode encerrar o jogo com segurança (em vez de tela preta).

Uso:
    python3 tools/patch_engine.sh            # aplica no jniLibs do app
    python3 tools/patch_engine.py <so>       # aplica num .so solto

O binário original vem do release do port (pinado por sha256 em
tools/fetch_engine.sh); a licença é BSD-3 e permite modificar e redistribuir,
mantendo o aviso.
"""
from __future__ import annotations

import hashlib
import os
import struct
import subprocess
import sys

# Offsets dentro do librexruntime.so do release v0.3.3 do port.
#   0x454c9c: `b 0x455508`  (saída do bloco de falha do submit)
#   0x455508: `mov w21, #3`  (kNotPresentedConnectionOutdated, bloco compartilhado)
#   0x7b8b68: 25 bytes de zeros no .text (espaço livre para o "code cave")
PATCH_SITE = 0x454C9C
SHARED_RETURN = 0x455508
CAVE = 0x7B8B68
CAVE_SIZE = 25

EXPECTED = {
    PATCH_SITE: bytes.fromhex("1b020014"),   # b 0x455508
    SHARED_RETURN: bytes.fromhex("75008052"),  # mov w21, #3
    CAVE: bytes(CAVE_SIZE),                 # zeros
}

# "VulkanPresenter: Failed to submit command buffers" — prova de que este
# .so é o binário esperado (o patch depende do layout exato).
SIGNATURE = b"VulkanPresenter: Failed to submit command buffers"


def branch(source: int, target: int) -> bytes:
    """`b target` (ARM64, imm26 em palavras)."""
    offset = (target - source) // 4
    if not -(1 << 25) <= offset < (1 << 25):
        raise ValueError("branch fora de alcance")
    return struct.pack("<I", 0x14000000 | (offset & 0x03FFFFFF))


def patch(data: bytearray) -> bool:
    for offset, expected in EXPECTED.items():
        actual = bytes(data[offset:offset + len(expected)])
        if actual != expected:
            print(f"!! esperado {expected.hex()} em 0x{offset:x}, achei {actual.hex()}")
            print("   (binário diferente do esperado — não vou patchear no escuro)")
            return False
    if data.find(SIGNATURE) < 0:
        print("!! assinatura do presenter não encontrada — binário inesperado")
        return False

    # Code cave: "mov w21, #5" (kGpuLostResponsible) + "b 0x45550c" (epilogue
    # logo depois do "mov w21, #3" compartilhado).
    # Em ARM64 o alvo do branch e relativo ao PC da propria instrucao.
    cave = struct.pack("<I", 0x52800000 | (5 << 5) | 21)
    cave += branch(CAVE + 4, SHARED_RETURN + 4)
    data[CAVE:CAVE + len(cave)] = cave

    # O bloco de falha do submit passa a cair no cave.
    data[PATCH_SITE:PATCH_SITE + 4] = branch(PATCH_SITE, CAVE)
    return True


def main() -> int:
    target = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
        "app/src/main/jniLibs/arm64-v8a/librexruntime.so")
    with open(target, "rb") as handle:
        data = bytearray(handle.read())
    if not patch(data):
        return 1
    with open(target, "wb") as handle:
        handle.write(data)
    print(f"patch aplicado: {target}")
    print(f"  md5: {hashlib.md5(data).hexdigest()}")
    print("  0x%x: b 0x%x (antes: b 0x455508)" % (PATCH_SITE, CAVE))
    print("  0x%x: mov w21, #5 ; b 0x%x" % (CAVE, SHARED_RETURN + 4))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
