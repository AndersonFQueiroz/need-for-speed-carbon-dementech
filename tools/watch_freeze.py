#!/usr/bin/env python3
"""Dementech · NFS Carbon — vigia de congelamento em tempo real.

Fica olhando o log do motor no aparelho e avisa quando a apresentação de
quadros para (o congelamento), sem precisar de ninguém olhando a tela.

    python3 tools/watch_freeze.py [--interval 30] [--alert 60]

Como funciona: lê a cauda do carbon.log do aparelho por adb, extrai o
contador cumulativo de quadros apresentados ("adquisicion anticipada: N de M
fotogramas") e o último timestamp do log. Se o log continua sendo escrito
(motor vivo) mas o contador não avança, a imagem está travada.

Sai com Ctrl-C. O histórico fica em /tmp/nfs-carbon-watch.log.
"""
from __future__ import annotations

import argparse
import datetime
import re
import subprocess
import sys
import time

PKG = "com.nfscarbon.android"
REMOTE_LOG = f"/sdcard/Android/data/{PKG}/files/logs/carbon.log"
TAIL = "tail -c 120000"

PRESENT = re.compile(r"adquisicion anticipada: (\d+) de (\d+) fotogramas")
STAMP = re.compile(r"\[(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\]")
FRAME = re.compile(r"fotograma de ([\d.]+) ms")


def shell(command: str) -> str:
    try:
        out = subprocess.run(["adb", "shell", command], capture_output=True, text=True, timeout=40)
        return out.stdout
    except (subprocess.TimeoutExpired, OSError) as error:
        return f"__erro__ {error}"


def parse_tail(text: str) -> dict:
    stamps = STAMP.findall(text)
    presented = PRESENT.findall(text)
    frames = FRAME.findall(text)
    return {
        "last_stamp": stamps[-1] if stamps else "",
        "presented": int(presented[-1][1]) if presented else None,
        "acquired": int(presented[-1][0]) if presented else None,
        "frame_ms": float(frames[-1]) if frames else None,
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--interval", type=int, default=30, help="segundos entre leituras")
    ap.add_argument("--alert", type=int, default=60, help="segundos sem apresentar para alertar")
    ap.add_argument("--log", default="/tmp/nfs-carbon-watch.log")
    args = ap.parse_args()

    def note(text: str) -> None:
        line = f"[{datetime.datetime.now():%H:%M:%S}] {text}"
        print(line, flush=True)
        with open(args.log, "a", encoding="utf-8") as fh:
            fh.write(line + "\n")

    note("vigia iniciada (intervalo %ds, alerta %ds)" % (args.interval, args.alert))
    last_presented = None
    last_change = time.time()
    frozen = False
    while True:
        try:
            raw = shell(f"{TAIL} {REMOTE_LOG}")
            if raw.startswith("__erro__") or "No such file" in raw:
                note("log indisponível (app fechada?) — tentando de novo")
                time.sleep(args.interval)
                continue
            data = parse_tail(raw)
            now = time.time()
            presented = data["presented"]
            if presented is not None:
                if last_presented is None:
                    last_presented = presented
                    last_change = now
                    note("apresentação em %d (log até %s)" % (presented, data["last_stamp"]))
                elif presented != last_presented:
                    fps = (presented - last_presented) / max(now - last_change, 0.001)
                    last_presented = presented
                    last_change = now
                    if frozen:
                        note("✓ apresentação voltou (contador %d)" % presented)
                        frozen = False
                    elif now - last_change < 300:
                        note("apresentando: %d (+%d, ~%.0f fps, quadro %s ms)"
                             % (presented, presented, fps, data["frame_ms"]))
                else:
                    idle = now - last_change
                    if idle >= args.alert and not frozen:
                        frozen = True
                        note("⚠⚠ CONGELAMENTO: %d s sem entregar quadro na tela "
                             "(motor continua escrevendo o log, último %s)" % (idle, data["last_stamp"]))
                        note("   → no aparelho: volte ao launcher (botão VOLTAR) e veja a aba Diagnóstico")
                    elif frozen and idle >= args.alert + 60:
                        note("   ainda travado (%d s)" % idle)
            time.sleep(args.interval)
        except KeyboardInterrupt:
            note("vigia encerrada")
            return 0


if __name__ == "__main__":
    raise SystemExit(main())
