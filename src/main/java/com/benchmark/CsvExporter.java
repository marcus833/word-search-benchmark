package com.benchmark;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

public class CsvExporter {

    public static void export(List<BenchmarkResult> results, String filePath) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
            pw.println("Arquivo,Metodo,Execucao,Ocorrencias,Tempo_ms");
            for (BenchmarkResult r : results) {
                pw.printf("%s,%s,%d,%d,%d%n",
                    r.fileName(), r.method(), r.run(), r.wordCount(), r.elapsedMs());
            }
        }
    }
}
