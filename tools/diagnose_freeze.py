#!/usr/bin/env python3
"""Dementech · NFS Carbon — analise do log nativo (carbon.log).

Le o log do motor (carbon.log / carbon.N.log) e mede o que importa para o
diagnostico de congelamento:

  * distribuicao de tempo de quadro (p50/p90/p99/max) e FPS implicito;
  * **stalls reais**: intervalo maximo entre dois quadros seguidos (o motor
    para de registrar quadro = a imagem parou de andar);
  * FPS ao longo do tempo (bucket de 60 s) -> mostra a queda progressiva;
  * quadros descartados na apresentacao ("swapchain outdated");
  * tempo ocioso/ocupado do anel de render;
  * tipos de aviso e erro agregados.

Uso:
    python3 tools/diagnose_freeze.py <carbon.log> [carbon.3.log ...]
    python3 tools/diagnose_freeze.py --json <logs...>
"""
from __future__ import annotations

import argparse
import json
import os
import re
import statistics
import sys
from collections import Counter
from dataclasses import dataclass, field
from datetime import datetime

TS = re.compile(r"\[(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\]")
FRAME = re.compile(r"fotograma de ([\d.]+) ms")
GPU = re.compile(r"GPU ([\d.]+) reales")
DROP = re.compile(r"Presentation to the swapchain image has been dropped", re.I)
PRESENT = re.compile(r"adquisicion anticipada: (\d+) de (\d+) fotogramas sin espera", re.I)
RING = re.compile(r"anillo: sin trabajo ([\d.]+) ms")
SWAPS = re.compile(
    r"intervalos entre Swaps: (.*?);\s*peor fotograma ([\d.]+) ms")
RING_BUSY = re.compile(r"anillo: \d+ dibujos, trabajando ([\d.]+) ms")
LEVEL = re.compile(r"\[(error|warn|warning|critical|fatal)\]", re.I)
TAG = re.compile(r"\[(tiron|nativo|presentador|xma|io|krnl|core|d3d_marcador|carbon|sh|audio|fs|sys)\]")


@dataclass
class Gap:
    start: datetime
    end: datetime
    before: str = ""
    after: str = ""


@dataclass
class Report:
    file: str = ""
    lines: int = 0
    bytes: int = 0
    first: datetime | None = None
    last: datetime | None = None
    frames: list[float] = field(default_factory=list)
    frame_ts: list[datetime] = field(default_factory=list)
    gpu: list[float] = field(default_factory=list)
    ring_idle: list[float] = field(default_factory=list)
    ring_busy: list[float] = field(default_factory=list)
    dropped: int = 0
    swaps: list[tuple[datetime, dict, float]] = field(default_factory=list)
    presented: list[tuple[datetime, int]] = field(default_factory=list)
    ts_all: list[datetime] = field(default_factory=list)
    levels: Counter = field(default_factory=Counter)
    warns: Counter = field(default_factory=Counter)
    gaps: list[Gap] = field(default_factory=list)
    frame_stalls: list[Gap] = field(default_factory=list)
    samples: list[str] = field(default_factory=list)

    def merge(self, other: "Report") -> None:
        self.lines += other.lines
        self.bytes += other.bytes
        self.frames += other.frames
        self.frame_ts += other.frame_ts
        self.gpu += other.gpu
        self.ring_idle += other.ring_idle
        self.ring_busy += other.ring_busy
        self.dropped += other.dropped
        self.presented += other.presented
        self.swaps += other.swaps
        self.ts_all += other.ts_all
        self.levels.update(other.levels)
        self.warns.update(other.warns)
        self.gaps += other.gaps
        self.frame_stalls += other.frame_stalls
        self.samples += other.samples
        if other.first and (self.first is None or other.first < self.first):
            self.first = other.first
        if other.last and (self.last is None or other.last > self.last):
            self.last = other.last


def normalize(message: str) -> str:
    message = re.sub(r"0x[0-9A-Fa-f]+", "0xN", message)
    message = re.sub(r"\b\d+[.,]?\d*\b", "N", message)
    message = re.sub(r"\s+", " ", message).strip()
    return message[:120]


def analyze(path: str) -> Report:
    rep = Report(file=path)
    prev_ts: datetime | None = None
    prev_line = ""
    last_frame_ts: datetime | None = None
    last_frame_line = ""
    with open(path, "r", encoding="utf-8", errors="replace") as fh:
        for raw in fh:
            rep.lines += 1
            line = raw.rstrip("\n")
            m = TS.search(line)
            if not m:
                continue
            ts = datetime.strptime(m.group(1), "%Y-%m-%d %H:%M:%S.%f")
            if rep.first is None:
                rep.first = ts
            rep.last = ts
            rep.ts_all.append(ts)
            if prev_ts is not None:
                delta = (ts - prev_ts).total_seconds()
                if delta >= 1.0:
                    rep.gaps.append(Gap(prev_ts, ts, prev_line[:160], line[:160]))
            prev_ts = ts
            prev_line = line

            fm = FRAME.search(line)
            if fm:
                rep.frames.append(float(fm.group(1)))
                if last_frame_ts is not None:
                    gap = (ts - last_frame_ts).total_seconds()
                    if gap >= 2.0:
                        rep.frame_stalls.append(Gap(last_frame_ts, ts, last_frame_line[:160], line[:160]))
                rep.frame_ts.append(ts)
                last_frame_ts = ts
                last_frame_line = line
            gm = GPU.search(line)
            if gm:
                rep.gpu.append(float(gm.group(1)))
            if DROP.search(line):
                rep.dropped += 1
            sm = SWAPS.search(line)
            if sm:
                buckets = {}
                for part in sm.group(1).split("|"):
                    part = part.strip()
                    match = re.match(r"^(.+?)(?:\s*ms)?\s+(\d+)$", part)
                    if match:
                        buckets[match.group(1).strip()] = int(match.group(2))
                try:
                    rep.swaps.append((ts, buckets, float(sm.group(2))))
                except ValueError:
                    pass
            pm = PRESENT.search(line)
            if pm:
                rep.presented.append((ts, int(pm.group(2))))
            rm = RING.search(line)
            if rm:
                rep.ring_idle.append(float(rm.group(1)))
            rb = RING_BUSY.search(line)
            if rb:
                rep.ring_busy.append(float(rb.group(1)))
            lm = LEVEL.search(line)
            if lm:
                key = lm.group(1).lower()
                rep.levels[key] += 1
                if key in ("warn", "warning"):
                    rep.warns[normalize(line)] += 1
                if key in ("error", "critical", "fatal") and len(rep.samples) < 25:
                    rep.samples.append(line[:200])
    try:
        rep.bytes = os.path.getsize(path)
    except OSError:
        pass
    return rep


def pct(values: list[float], p: float) -> float:
    if not values:
        return 0.0
    s = sorted(values)
    k = max(0, min(len(s) - 1, int(round((p / 100) * (len(s) - 1)))))
    return s[k]


def fps_buckets(rep: Report, bucket_s: int = 60) -> list[tuple[str, int, float]]:
    """(inicio, quadros no bucket, fps medio do bucket)."""
    if not rep.frame_ts:
        return []
    out = []
    start = rep.frame_ts[0]
    buckets: dict[datetime, list[datetime]] = {}
    for ts in rep.frame_ts:
        key = start.replace(second=0, microsecond=0)
        offset = int((ts - key).total_seconds() // bucket_s)
        buckets.setdefault(offset, []).append(ts)
    for offset in sorted(buckets):
        items = buckets[offset]
        span = max(bucket_s, (items[-1] - items[0]).total_seconds() + 0.001)
        out.append((f"+{offset*bucket_s}s", len(items), len(items) / span))
    return out


def presentation_windows(rep: Report, min_gap_s: float = 2.0):
    """Janelas em que o motor PAROU de apresentar quadros.

    O contador do apresentador ("adquisicion anticipada: N de M fotogramas") e
    cumulativo. Se M para de crescer enquanto o log continua (o jogo vivo), a
    imagem na tela fica parada: e exatamente o congelamento relatado.
    """
    samples = sorted(rep.presented)
    out = []
    for (t1, m1), (t2, m2) in zip(samples, samples[1:]):
        gap = (t2 - t1).total_seconds()
        if gap >= min_gap_s and m2 - m1 == 0:
            alive = sum(1 for t in rep.ts_all if t1 < t <= t2)
            out.append((t1, t2, gap, alive))
    return out


def render(rep: Report, args) -> str:
    out: list[str] = []
    out.append("=" * 74)
    out.append("Dementech · NFS Carbon — analise do log nativo")
    out.append("=" * 74)
    out.append(f"arquivo(s)   : {rep.file}")
    out.append(f"linhas       : {rep.lines:,}  ({rep.bytes/1e6:.1f} MB)")
    if rep.first and rep.last:
        span = (rep.last - rep.first).total_seconds()
        out.append(f"janela       : {rep.first} -> {rep.last}  ({span/60:.1f} min)")
    out.append("")

    out.append("-- tempo de quadro ------------------------------------------------")
    if rep.frames:
        p50, p90, p99 = pct(rep.frames, 50), pct(rep.frames, 90), pct(rep.frames, 99)
        out.append(f"quadros      : {len(rep.frames):,}")
        out.append(f"p50          : {p50:8.2f} ms   (~{1000/max(p50,0.01):.1f} fps)")
        out.append(f"p90          : {p90:8.2f} ms   (~{1000/max(p90,0.01):.1f} fps)")
        out.append(f"p99          : {p99:8.2f} ms   (~{1000/max(p99,0.01):.1f} fps)")
        out.append(f"max          : {max(rep.frames):8.2f} ms")
        out.append(f"media        : {statistics.fmean(rep.frames):8.2f} ms")
        slow = sum(1 for f in rep.frames if f > 100)
        out.append(f"quadros >100ms: {slow:,} ({100*slow/len(rep.frames):.2f} %)")
    else:
        out.append("sem amostras de tempo de quadro")
    if rep.gpu:
        out.append(f"GPU real p50 : {pct(rep.gpu,50):8.2f} ms | p90 {pct(rep.gpu,90):.2f} | max {max(rep.gpu):.2f} ms")
        out.append("  (tempo de GPU << tempo de quadro => o gargalo NAO e a GPU)")
    out.append("")

    out.append("-- stalls reais (sem registrar quadro) -----------------------------")
    stalls = sorted(rep.frame_stalls, key=lambda g: (g.end - g.start).total_seconds(), reverse=True)
    if stalls:
        out.append(f"stalls >= 2s : {len(stalls)}  (total {(sum((g.end-g.start).total_seconds() for g in stalls)):.1f}s)")
        for g in stalls[: args.top]:
            d = (g.end - g.start).total_seconds()
            out.append(f"  {d:7.2f}s  {g.start.time()} -> {g.end.time()}")
            if args.context:
                out.append(f"      antes : {g.before}")
                out.append(f"      depois: {g.after}")
    else:
        out.append("nenhum stall >= 2s entre quadros")
    out.append("")

    out.append("-- FPS ao longo do tempo (bucket de 60 s) -------------------------")
    buckets = fps_buckets(rep)
    if buckets:
        for label, count, fps in buckets[: args.top * 3]:
            bar = "#" * int(min(fps, 60))
            out.append(f"  {label:>8}  {count:>5} quadros  {fps:6.1f} fps  {bar}")
    out.append("")

    out.append("-- ritmo real (histograma de swaps do motor) -----------------------")
    if rep.swaps:
        for ts, buckets, worst in rep.swaps[-args.top:]:
            total = sum(buckets.values()) or 1
            bad = sum(n for k, n in buckets.items()
                      if re.match(r"(>=?1\d\d|[6-9]\d-|[1-9]\d\d-\d+)", k.replace(" ", "")) and n)
            fast = buckets.get("<15 ms", 0) + buckets.get("15-18", 0) + buckets.get("18-25", 0)
            out.append(f"  {ts.time()}  quadros={total:<5} piores={worst:8.1f} ms  "
                       f"lentos(>=60ms)={bad:<4} rapidos(<25ms)={fast}")
    else:
        out.append("sem linhas de histograma de swaps")
    out.append("")

    out.append("-- apresentacao / anel ---------------------------------------------")
    out.append(f"apresentacoes descartadas (swapchain outdated): {rep.dropped:,}")
    if rep.presented:
        first_m = rep.presented[0][1]
        last_m = max(m for _, m in rep.presented)
        out.append(f"quadros apresentados (contador do apresentador): {first_m:,} -> {last_m:,} (+{last_m-first_m:,})")
        wins = presentation_windows(rep)
        if wins:
            total = sum(w[2] for w in wins)
            out.append(f"janelas sem apresentacao (>= 2s): {len(wins)}  ({total:.0f}s)")
            for t1, t2, gap, alive in wins[: args.top]:
                out.append(f"  {gap:7.1f}s  {t1.time()} -> {t2.time()}   linhas de log no periodo: {alive}")
            out.append("  (log continuando + apresentacao parada = imagem congelada, motor vivo)")
        else:
            out.append("sem janelas sem apresentacao (>= 2s)")
    if rep.ring_idle:
        out.append(f"anel ocioso  : p50 {pct(rep.ring_idle,50):.1f} ms | p90 {pct(rep.ring_idle,90):.1f} ms | max {max(rep.ring_idle):.1f} ms")
    if rep.ring_busy:
        out.append(f"anel ocupado : p50 {pct(rep.ring_busy,50):.1f} ms | p90 {pct(rep.ring_busy,90):.1f} ms | max {max(rep.ring_busy):.1f} ms")
    out.append("")

    if rep.levels:
        out.append("-- avisos e erros -------------------------------------------------")
        for k, v in sorted(rep.levels.items(), key=lambda kv: -kv[1]):
            out.append(f"  {k:<10} {v:,}")
        if rep.warns:
            out.append("  avisos mais frequentes:")
            for msg, n in rep.warns.most_common(args.top):
                out.append(f"    {n:>6}x {msg}")
        if rep.samples:
            out.append("  amostras de erro:")
            for s in rep.samples[: args.top]:
                out.append(f"    {s}")
        out.append("")
    return "\n".join(out)


def session_rows(reps: list[Report]) -> list[dict]:
    rows = []
    for rep in reps:
        wins = presentation_windows(rep)
        pres_delta = 0
        if rep.presented:
            pres_delta = max(m for _, m in rep.presented) - min(m for _, m in rep.presented)
        span = (rep.last - rep.first).total_seconds() if rep.first and rep.last else 0
        rows.append({
            "file": os.path.basename(rep.file),
            "inicio": str(rep.first)[5:19] if rep.first else "-",
            "min": round(span / 60, 1),
            "quadros": len(rep.frames),
            "p50_ms": round(pct(rep.frames, 50), 1),
            "p90_ms": round(pct(rep.frames, 90), 1),
            "max_ms": round(max(rep.frames)) if rep.frames else 0,
            "gpu_p50_ms": round(pct(rep.gpu, 50), 1),
            "pct_lento": round(100 * sum(1 for f in rep.frames if f > 100) / len(rep.frames), 1) if rep.frames else 0,
            "apresentados": pres_delta,
            "apresent_fps": round(pres_delta / span, 1) if span else 0,
            "descartados": rep.dropped,
            "janelas_sem_apresent": len(wins),
            "seg_parado": round(sum(w[2] for w in wins)),
        })
    return rows


def render_sessions(reps: list[Report]) -> str:
    rows = session_rows(reps)
    head = ("arquivo", "inicio", "min", "quadros", "p50 ms", "p90 ms", "max ms",
            "GPU p50", "%>100ms", "apresent.", "apres.fps", "descart.", "janelas", "s parado")
    widths = [max(len(str(r[i])) for r in rows + [{"x": None}]) if False else 0 for i in range(0)]
    out = []
    out.append("=" * 118)
    out.append("Dementech · NFS Carbon — resumo por sessao (um log = uma sessao de jogo)")
    out.append("=" * 118)
    out.append("  ".join(f"{h:<12}" for h in head))
    out.append("-" * 118)
    for r in rows:
        out.append("  ".join([
            f"{r['file'][:12]:<12}", f"{r['inicio']:<12}", f"{r['min']:<12}",
            f"{r['quadros']:<12}", f"{r['p50_ms']:<12}", f"{r['p90_ms']:<12}", f"{r['max_ms']:<12}",
            f"{r['gpu_p50_ms']:<12}", f"{r['pct_lento']:<12}", f"{r['apresentados']:<12}",
            f"{r['apresent_fps']:<12}", f"{r['descartados']:<12}", f"{r['janelas_sem_apresent']:<12}",
            f"{r['seg_parado']:<12}"]))
    out.append("-" * 118)
    out.append("p50/p90/max ms = tempo do quadro registrado pelo motor (amostra de quadros notaveis).")
    out.append("GPU p50 = tempo real na GPU. presenting = quadros entregues ao display.")
    out.append("'janelas' = periodos em que o motor NAO apresentou quadro (>= 2s) com o log ativo.")
    return "\n".join(out)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("logs", nargs="+", help="carbon.log e/ou carbon.N.log")
    ap.add_argument("--json", action="store_true", help="saida em JSON")
    ap.add_argument("--top", type=int, default=10, help="quantos itens detalhar")
    ap.add_argument("--context", action="store_true", help="mostrar linhas ao redor dos stalls")
    ap.add_argument("--sessions", action="store_true", help="resumo por sessao (um log = uma sessao)")
    args = ap.parse_args()

    reps = []
    for path in args.logs:
        try:
            reps.append(analyze(path))
        except OSError as exc:
            print(f"aviso: nao consegui ler {path}: {exc}", file=sys.stderr)

    if args.sessions or (len(reps) > 1 and not args.json):
        print(render_sessions(reps))
        print()

    total = Report(file=", ".join(args.logs))
    for rep in reps:
        total.merge(rep)

    if args.json:
        print(json.dumps({
            "files": args.logs,
            "lines": total.lines,
            "bytes": total.bytes,
            "first": str(total.first),
            "last": str(total.last),
            "frames": {
                "count": len(total.frames),
                "p50": pct(total.frames, 50),
                "p90": pct(total.frames, 90),
                "p99": pct(total.frames, 99),
                "max": max(total.frames) if total.frames else 0,
                "over_100ms": sum(1 for f in total.frames if f > 100),
            },
            "gpu_ms": {"p50": pct(total.gpu, 50), "max": max(total.gpu) if total.gpu else 0},
            "dropped_presentations": total.dropped,
            "ring_idle": {"p50": pct(total.ring_idle, 50), "max": max(total.ring_idle) if total.ring_idle else 0},
            "ring_busy": {"p50": pct(total.ring_busy, 50), "max": max(total.ring_busy) if total.ring_busy else 0},
            "stalls": sorted(
                [{"seconds": round((g.end - g.start).total_seconds(), 2),
                  "at": str(g.start), "before": g.before, "after": g.after} for g in total.frame_stalls],
                key=lambda d: -d["seconds"])[: args.top],
            "fps_buckets": [{"at": a, "frames": b, "fps": round(c, 2)} for a, b, c in fps_buckets(total)],
            "levels": dict(total.levels),
            "top_warnings": [{"n": n, "msg": m} for m, n in total.warns.most_common(args.top)],
        }, indent=2, ensure_ascii=False))
    else:
        print(render(total, args))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
