package com.benchmark;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ChartGenerator {
    public static void generate(List<BenchmarkResult> results, String outputDir) throws IOException {
        new File(outputDir).mkdirs();
        System.out.println("  → Execute 'python3 generate_charts.py results/benchmark_results.csv results/' para gerar os gráficos.");
    }
}
