#!/usr/bin/env python3
"""plot_results.py — render perflib benchmark CSV into speedup/throughput charts.

Usage:
    python3 tools/plot_results.py results.csv [--out charts/] [--show]

Input CSV columns (emitted by perflib_bench, mirrors the JSON schema):
    routine,impl,size,ns_per_call,throughput,unit,speedup

matplotlib is optional: if it is not installed the script prints an ASCII
summary table instead, so it still works in a bare CI container.
"""
from __future__ import annotations

import argparse
import csv
import os
import sys
from collections import defaultdict


def load(path: str) -> list[dict]:
    with open(path, newline="") as f:
        rows = list(csv.DictReader(f))
    for r in rows:
        r["size"] = int(r["size"])
        r["throughput"] = float(r["throughput"])
        r["speedup"] = float(r["speedup"])
    return rows


def ascii_summary(rows: list[dict]) -> None:
    print(f"{'routine':<8}{'size':>10}{'impl':>6}{'throughput':>14}{'speedup':>10}")
    print("-" * 48)
    for r in sorted(rows, key=lambda r: (r["routine"], r["size"], r["impl"])):
        print(f"{r['routine']:<8}{r['size']:>10}{r['impl']:>6}"
              f"{r['throughput']:>11.2f} {r['unit']:<3}{r['speedup']:>9.2f}x")


def plot(rows: list[dict], out_dir: str, show: bool) -> None:
    try:
        import matplotlib
        if not show:
            matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except ImportError:
        print("[plot_results] matplotlib not available; printing ASCII summary.\n",
              file=sys.stderr)
        ascii_summary(rows)
        return

    os.makedirs(out_dir, exist_ok=True)
    # AVX2 speedup vs size, one line per routine.
    by_routine: dict[str, list[tuple[int, float]]] = defaultdict(list)
    for r in rows:
        if r["impl"] == "avx2":
            by_routine[r["routine"]].append((r["size"], r["speedup"]))

    plt.figure()
    for routine, pts in sorted(by_routine.items()):
        pts.sort()
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        plt.plot(xs, ys, marker="o", label=routine)
    plt.axhline(1.0, linestyle="--", linewidth=1, label="C baseline")
    plt.xscale("log", base=2)
    plt.xlabel("problem size")
    plt.ylabel("speedup vs auto-vectorized C (x)")
    plt.title("perflib: AVX2 speedup over compiler auto-vectorization")
    plt.legend()
    plt.grid(True, which="both", alpha=0.3)
    out_path = os.path.join(out_dir, "speedup.png")
    plt.savefig(out_path, dpi=120, bbox_inches="tight")
    print(f"[plot_results] wrote {out_path}")
    if show:
        plt.show()


def main() -> int:
    ap = argparse.ArgumentParser(description="Plot perflib benchmark results.")
    ap.add_argument("csv", help="results CSV from perflib_bench")
    ap.add_argument("--out", default="charts", help="output directory for PNGs")
    ap.add_argument("--show", action="store_true", help="open an interactive window")
    args = ap.parse_args()

    if not os.path.exists(args.csv):
        print(f"error: no such file: {args.csv}", file=sys.stderr)
        return 1
    rows = load(args.csv)
    if not rows:
        print("error: empty CSV", file=sys.stderr)
        return 1
    plot(rows, args.out, args.show)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
