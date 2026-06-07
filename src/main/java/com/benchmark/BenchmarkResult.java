package com.benchmark;

public class BenchmarkResult {

    private final String fileName;
    private final String method;
    private final int    run;
    private final long   wordCount;
    private final long   elapsedMs;

    public BenchmarkResult(String fileName, String method, int run, long wordCount, long elapsedMs) {
        this.fileName  = fileName;
        this.method    = method;
        this.run       = run;
        this.wordCount = wordCount;
        this.elapsedMs = elapsedMs;
    }

    public String fileName()  { return fileName;  }
    public String method()    { return method;    }
    public int    run()       { return run;       }
    public long   wordCount() { return wordCount; }
    public long   elapsedMs() { return elapsedMs; }

    @Override
    public String toString() {
        return String.format("BenchmarkResult[%s, %s, run=%d, count=%d, %dms]",
                fileName, method, run, wordCount, elapsedMs);
    }
}