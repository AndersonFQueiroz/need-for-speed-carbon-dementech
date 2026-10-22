#!/usr/bin/env python3
"""Dementech · NFS Carbon — relatório de uma sessão de jogo (métrica do A/B).

Lê o `carbon.log` do motor e responde, em uma tela:

  * quanto tempo a sessão durou e quantas vezes o device Vulkan morreu;
  * quanto a GPU trabalhou por quadro (media, maximo, quadro lento);
  * quanto trabalho foi jogado fora (copias, borrados, esperas a GPU);
  * quantos videos foram reproduzidos (cada video decodifica e sobe textura
    1280x720 para a GPU).

Uso:
    python3 tools/bench_session.py <carbon.log> [--desde "YYYY-MM-DD HH:MM:SS"]
                                    [--ate "YYYY-MM-DD HH:MM:SS"]

Exemplo (so a janela do teste):
    python3 tools/bench_session.py /tmp/carbon.log --desde "2026-10-22 01:01:00" \\
        --ate "2026-10-22 01:27:00"
"""
from __future__ import annotations

import argparse
import re
import statistics
import sys
from datetime import datetime

STAMP = re.compile(r"^\[(\d{4}-\d\d-\d\d \d\d:\d\d:\d\d)\.")
# "[tiron] fotograma de 66.7 ms (GPU 22.30 reales, grabar 0.50): 16 copias, 12 borrados, ..."
TIRON = re.compile(
    r"fotograma de ([\d.]+) ms \(GPU ([\d.]+) reales.*?\): (\d+) copias, (\d+) borrados")
GPU_WAIT = re.compile(r"borrados, \d+ intercambios, \d+ restauraciones, ([\d.]+) esperas")
PRESENT = re.compile(r"presentar ([\d.]+) ms/Swap")
GPU_SWAP = re.compile(r"GPU ([\d.]+) ms por Swap")
STATS = re.compile(r"\[nativo\] C2: copias=(\d+).*?borrados=(\d+).*?GPU ([\d.]+) ms por Swap")
DEAD = ("VK_ERROR_DEVICE_LOST", "timestamp wait failed", "Protocol error", "device has been lost")
VIDEO = re.compile(r"\[video\].*?abre '([^']+)'")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("log")
    ap.add_argument("--desde")
    ap.add_argument("--ate")
    args = ap.parse_args()

    try:
        with open(args.log, encoding="utf-8", errors="replace") as handle:
            linhas = handle.read().split("\n")
    except OSError as error:
        print(f"nao consegui ler {args.log}: {error}")
        return 1

    ini = datetime.strptime(args.desde, "%Y-%m-%d %H:%M:%S") if args.desde else None
    fim = datetime.strptime(args.ate, "%Y-%m-%d %H:%M:%S") if args.ate else None

    frames: list[float] = []
    gpu: list[float] = []
    copias: list[int] = []
    borrados: list[int] = []
    esperas: list[float] = []
    apresentar: list[float] = []
    swap: list[float] = []
    mortes: list[str] = []
    videos: list[str] = []
    primeiro = ultimo = None

    for line in linhas:
        match = STAMP.match(line)
        if not match:
            continue
        momento = datetime.strptime(match.group(1), "%Y-%m-%d %H:%M:%S")
        if ini and momento < ini:
            continue
        if fim and momento > fim:
            continue
        if primeiro is None:
            primeiro = momento
        ultimo = momento
        if any(chave in line for chave in DEAD):
            mortes.append(momento.strftime("%H:%M:%S"))
        video = VIDEO.search(line)
        if video:
            videos.append(video.group(1).split("\\")[-1])
        tiro = TIRON.search(line)
        if tiro:
            frames.append(float(tiro.group(1)))
            gpu.append(float(tiro.group(2)))
            copias.append(int(tiro.group(3)))
            borrados.append(int(tiro.group(4)))
            espera = GPU_WAIT.search(line)
            if espera:
                esperas.append(float(espera.group(1)))
        found = PRESENT.search(line)
        if found:
            apresentar.append(float(found.group(1)))
        found = GPU_SWAP.search(line)
        if found:
            swap.append(float(found.group(1)))

    if primeiro is None:
        print("sem linhas na janela pedida")
        return 1

    def stat(valores: list[float], digitos: int = 2) -> str:
        if not valores:
            return "n/d"
        return (f"media {statistics.fmean(valores):.{digitos}f} | "
                f"mediana {statistics.median(valores):.{digitos}f} | "
                f"max {max(valores):.{digitos}f}")

    minutos = (ultimo - primeiro).total_seconds() / 60.0
    print(f"sessao: {primeiro} -> {ultimo}  ({minutos:.1f} min)")
    print(f"mortes do device: {len(mortes)}" + ("  <-- o congelamento" if mortes else "  (zero)"))
    print(f"quadros medidos ([tiron]): {len(frames)}")
    if frames:
        lentos = [f for f in frames if f > 60]
        print(f"  tempo de quadro  : {stat(frames, 1)} ms")
        print(f"  quadro lento >60ms: {len(lentos)} ({100.0 * len(lentos) / len(frames):.1f} %)")
    if gpu:
        print(f"  GPU por quadro    : {stat(gpu)} ms")
    if copias:
        print(f"  copias por quadro : media {statistics.fmean(copias):.1f}")
    if borrados:
        print(f"  borrados/quadro   : media {statistics.fmean(borrados):.1f}")
    if esperas:
        print(f"  esperas a GPU     : media {statistics.fmean(esperas):.2f} por quadro")
    if apresentar:
        print(f"  apresentar        : {stat(apresentar)} ms/Swap")
    if swap:
        print(f"  GPU por Swap      : {stat(swap)} ms")
    print(f"videos reproduzidos: {len(videos)}")
    if videos:
        unicos = {}
        for nome in videos:
            unicos[nome] = unicos.get(nome, 0) + 1
        for nome, total in sorted(unicos.items(), key=lambda x: -x[1])[:6]:
            print(f"  {total:3}x {nome}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
