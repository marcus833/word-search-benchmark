package com.benchmark;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {

    private static final String SEARCH_WORD = "the";

    private static final int N_RUNS = 5;

    private static final String[] FILES = {
        "data/Dracula.txt",
        "data/MobyDick.txt",
        "data/DonQuixote.txt"
    };

    public static void main(String[] args) throws Exception {
        System.out.println("=== Word Search Benchmark ===");
        System.out.printf("Palavra buscada: \"%s\" | Repetições por teste: %d%n%n", SEARCH_WORD, N_RUNS);

        List<BenchmarkResult> results = new ArrayList<>();
        WordSearcher searcher = new WordSearcher();

        for (String filePath : FILES) {
            Path path = Paths.get(filePath);
            String fileName = path.getFileName().toString().replace(".txt", "");
            System.out.println("--- Arquivo: " + fileName + " ---");

            String text;
            try {
                text = FileLoader.load(filePath);
            } catch (IOException e) {
                System.err.println("Arquivo não encontrado: " + filePath + " — pulando.");
                continue;
            }

            // --- SerialCPU ---
            for (int i = 0; i < N_RUNS; i++) {
                long start = System.currentTimeMillis();
                long count  = searcher.serialCPU(text, SEARCH_WORD);
                long elapsed = System.currentTimeMillis() - start;
                System.out.printf("  SerialCPU   [run %d]: %d ocorrências em %d ms%n", i + 1, count, elapsed);
                results.add(new BenchmarkResult(fileName, "SerialCPU", i + 1, count, elapsed));
            }

            // --- ParallelCPU ---
            for (int i = 0; i < N_RUNS; i++) {
                long start   = System.currentTimeMillis();
                long count   = searcher.parallelCPU(text, SEARCH_WORD);
                long elapsed = System.currentTimeMillis() - start;
                System.out.printf("  ParallelCPU [run %d]: %d ocorrências em %d ms%n", i + 1, count, elapsed);
                results.add(new BenchmarkResult(fileName, "ParallelCPU", i + 1, count, elapsed));
            }

            // --- ParallelGPU ---
            for (int i = 0; i < N_RUNS; i++) {
                long start   = System.currentTimeMillis();
                long count   = searcher.parallelGPU(text, SEARCH_WORD);
                long elapsed = System.currentTimeMillis() - start;
                System.out.printf("  ParallelGPU [run %d]: %d ocorrências em %d ms%n", i + 1, count, elapsed);
                results.add(new BenchmarkResult(fileName, "ParallelGPU", i + 1, count, elapsed));
            }

            System.out.println();
        }

        // Salva CSV
        String csvPath = "results/benchmark_results.csv";
        new java.io.File("results").mkdirs();
        CsvExporter.export(results, csvPath);
        System.out.println("CSV salvo em: " + csvPath);

        // Gera gráficos
        ChartGenerator.generate(results, "results/");
        System.out.println("Gráficos salvos em: results/");

        // Imprime tabela resumo
        printSummary(results);
    }

    private static void printSummary(List<BenchmarkResult> results) {
        System.out.println("\n=== Resumo (médias) ===");
        System.out.printf("%-20s %-14s %10s %10s%n", "Arquivo", "Método", "Count", "Tempo(ms)");
        System.out.println("-".repeat(58));

        Map<String, Map<String, List<Long>>> grouped = new LinkedHashMap<>();
        for (BenchmarkResult r : results) {
            grouped.computeIfAbsent(r.fileName(), k -> new LinkedHashMap<>())
                   .computeIfAbsent(r.method(), k -> new ArrayList<>())
                   .add(r.elapsedMs());
        }

        for (var fileEntry : grouped.entrySet()) {
            for (var methodEntry : fileEntry.getValue().entrySet()) {
                double avg = methodEntry.getValue().stream().mapToLong(Long::longValue).average().orElse(0);
                long count = results.stream()
                    .filter(r -> r.fileName().equals(fileEntry.getKey()) && r.method().equals(methodEntry.getKey()))
                    .mapToLong(BenchmarkResult::wordCount).findFirst().orElse(0);
                System.out.printf("%-20s %-14s %10d %10.1f%n",
                    fileEntry.getKey(), methodEntry.getKey(), count, avg);
            }
        }
    }
}
