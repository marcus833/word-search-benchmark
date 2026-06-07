#!/usr/bin/env python3
"""
generate_charts.py  –  Gera gráficos PNG a partir do CSV do benchmark.

Uso: python3 generate_charts.py <csv_path> <output_dir>
"""

import sys
import csv
import os
from collections import defaultdict
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
import numpy as np

# ── Paleta de cores ──────────────────────────────────────────────────────────
COLORS = {
    "SerialCPU":    "#4472C4",
    "ParallelCPU":  "#ED7D31",
    "ParallelGPU":  "#70AD47",
}
METHODS = ["SerialCPU", "ParallelCPU", "ParallelGPU"]

def load_csv(path):
    rows = []
    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            rows.append({
                "file":    row["Arquivo"],
                "method":  row["Metodo"],
                "run":     int(row["Execucao"]),
                "count":   int(row["Ocorrencias"]),
                "time_ms": float(row["Tempo_ms"]),
            })
    return rows

def group(rows):
    """file -> method -> [time_ms, ...]"""
    d = defaultdict(lambda: defaultdict(list))
    for r in rows:
        d[r["file"]][r["method"]].append(r["time_ms"])
    return d

def avg(lst):
    return sum(lst) / len(lst) if lst else 0

# ─────────────────────────────────────────────────────────────────────────────
# Gráfico 1 – Barras agrupadas: Tempo médio por arquivo × método
# ─────────────────────────────────────────────────────────────────────────────
def chart_grouped_bars(data, outdir):
    files = list(data.keys())
    x = np.arange(len(files))
    width = 0.25

    fig, ax = plt.subplots(figsize=(10, 6))
    for idx, method in enumerate(METHODS):
        avgs = [avg(data[f].get(method, [0])) for f in files]
        bars = ax.bar(x + idx * width, avgs, width,
                      label=method, color=COLORS[method], edgecolor="white", linewidth=0.8)
        for bar, val in zip(bars, avgs):
            ax.text(bar.get_x() + bar.get_width() / 2, bar.get_height() + 2,
                    f"{val:.0f}", ha="center", va="bottom", fontsize=8)

    ax.set_title("Tempo Médio de Execução por Arquivo e Método", fontsize=14, fontweight="bold")
    ax.set_xlabel("Arquivo de entrada", fontsize=11)
    ax.set_ylabel("Tempo médio (ms)", fontsize=11)
    ax.set_xticks(x + width)
    ax.set_xticklabels(files, fontsize=10)
    ax.legend(fontsize=10)
    ax.set_facecolor("#F8F8F8")
    fig.patch.set_facecolor("white")
    ax.grid(axis="y", linestyle="--", alpha=0.5)
    plt.tight_layout()
    path = os.path.join(outdir, "grafico_tempo_medio.png")
    plt.savefig(path, dpi=150)
    plt.close()
    print(f"  Salvo: {path}")

# ─────────────────────────────────────────────────────────────────────────────
# Gráfico 2 – Linha por execução (um gráfico por arquivo)
# ─────────────────────────────────────────────────────────────────────────────
def chart_runs_per_file(rows, data, outdir):
    files = list(data.keys())
    for fname in files:
        fig, ax = plt.subplots(figsize=(9, 5))
        for method in METHODS:
            times = data[fname].get(method, [])
            runs  = list(range(1, len(times) + 1))
            ax.plot(runs, times, marker="o", color=COLORS[method],
                    label=method, linewidth=2.2, markersize=6)
            for i, t in zip(runs, times):
                ax.annotate(f"{t:.0f}", (i, t), textcoords="offset points",
                            xytext=(0, 7), ha="center", fontsize=7.5)

        ax.set_title(f"Tempo por Execução – {fname}", fontsize=13, fontweight="bold")
        ax.set_xlabel("Execução (run)", fontsize=11)
        ax.set_ylabel("Tempo (ms)", fontsize=11)
        ax.set_xticks(list(range(1, max(len(data[fname].get(m, [])) for m in METHODS) + 1)))
        ax.legend(fontsize=10)
        ax.set_facecolor("#F8F8F8")
        fig.patch.set_facecolor("white")
        ax.grid(linestyle="--", alpha=0.4)
        plt.tight_layout()
        safe = fname.replace(" ", "_")
        path = os.path.join(outdir, f"grafico_execucoes_{safe}.png")
        plt.savefig(path, dpi=150)
        plt.close()
        print(f"  Salvo: {path}")

# ─────────────────────────────────────────────────────────────────────────────
# Gráfico 3 – Comparação entre métodos (barras horizontais com IC)
# ─────────────────────────────────────────────────────────────────────────────
def chart_comparison(data, outdir):
    files = list(data.keys())
    fig, axes = plt.subplots(1, len(files), figsize=(5 * len(files), 5), sharey=False)
    if len(files) == 1:
        axes = [axes]

    for ax, fname in zip(axes, files):
        avgs = [avg(data[fname].get(m, [0])) for m in METHODS]
        stds = [np.std(data[fname].get(m, [0])) for m in METHODS]
        colors = [COLORS[m] for m in METHODS]
        bars = ax.bar(METHODS, avgs, color=colors, edgecolor="white",
                      linewidth=0.8, yerr=stds, capsize=5, error_kw={"elinewidth": 1.5})
        for bar, val in zip(bars, avgs):
            ax.text(bar.get_x() + bar.get_width() / 2, bar.get_height() + max(stds) * 0.1 + 2,
                    f"{val:.0f}ms", ha="center", fontsize=9, fontweight="bold")
        ax.set_title(fname, fontsize=12, fontweight="bold")
        ax.set_ylabel("Tempo médio (ms)", fontsize=10)
        ax.set_facecolor("#F8F8F8")
        ax.grid(axis="y", linestyle="--", alpha=0.45)
        ax.tick_params(axis="x", labelsize=9)

    fig.suptitle("Comparação de Desempenho por Método (média ± desvio padrão)",
                 fontsize=13, fontweight="bold", y=1.02)
    fig.patch.set_facecolor("white")
    plt.tight_layout()
    path = os.path.join(outdir, "grafico_comparacao_metodos.png")
    plt.savefig(path, dpi=150, bbox_inches="tight")
    plt.close()
    print(f"  Salvo: {path}")

# ─────────────────────────────────────────────────────────────────────────────
# Gráfico 4 – Speedup: ParallelCPU e ParallelGPU em relação ao SerialCPU
# ─────────────────────────────────────────────────────────────────────────────
def chart_speedup(data, outdir):
    files = list(data.keys())
    x = np.arange(len(files))
    width = 0.3

    fig, ax = plt.subplots(figsize=(9, 5))
    for idx, method in enumerate(["ParallelCPU", "ParallelGPU"]):
        speedups = []
        for f in files:
            serial_avg = avg(data[f].get("SerialCPU", [1]))
            par_avg    = avg(data[f].get(method, [serial_avg]))
            speedups.append(serial_avg / par_avg if par_avg > 0 else 1.0)

        bars = ax.bar(x + idx * width, speedups, width,
                      label=method, color=COLORS[method], edgecolor="white")
        for bar, val in zip(bars, speedups):
            ax.text(bar.get_x() + bar.get_width() / 2, bar.get_height() + 0.02,
                    f"{val:.2f}×", ha="center", va="bottom", fontsize=8.5)

    ax.axhline(y=1.0, color="#4472C4", linestyle="--", linewidth=1.5, label="SerialCPU (baseline)")
    ax.set_title("Speedup vs. SerialCPU", fontsize=13, fontweight="bold")
    ax.set_xlabel("Arquivo", fontsize=11)
    ax.set_ylabel("Speedup (×)", fontsize=11)
    ax.set_xticks(x + width / 2)
    ax.set_xticklabels(files, fontsize=10)
    ax.legend(fontsize=10)
    ax.set_facecolor("#F8F8F8")
    fig.patch.set_facecolor("white")
    ax.grid(axis="y", linestyle="--", alpha=0.4)
    plt.tight_layout()
    path = os.path.join(outdir, "grafico_speedup.png")
    plt.savefig(path, dpi=150)
    plt.close()
    print(f"  Salvo: {path}")

# ─────────────────────────────────────────────────────────────────────────────
def main():
    csv_path  = sys.argv[1] if len(sys.argv) > 1 else "results/benchmark_results.csv"
    outdir    = sys.argv[2] if len(sys.argv) > 2 else "results/"
    os.makedirs(outdir, exist_ok=True)

    rows = load_csv(csv_path)
    data = group(rows)

    print("Gerando gráficos...")
    chart_grouped_bars(data, outdir)
    chart_runs_per_file(rows, data, outdir)
    chart_comparison(data, outdir)
    chart_speedup(data, outdir)
    print("Concluído.")

if __name__ == "__main__":
    main()
