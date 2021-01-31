import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BenchmarkManager {
    static Map<Integer, Benchmark> benchmarks = new HashMap<>();
    static List<String> benchmarkLog = new ArrayList<>();

    public static void startBenchmark(Integer id, String name) {
        if (!benchmarks.isEmpty() && benchmarks.containsKey(id)) {
            throw new IllegalArgumentException("There is currently a benchmark with that id already");
        }
        benchmarks.put(id, new Benchmark(name));
    }

    public static long finishBenchmark(Integer id) {
        if (!benchmarks.containsKey(id)) {
            throw new IllegalArgumentException("There is no benchmark with that id");
        }
        long millis = benchmarks.get(id).endBenchmark();
        benchmarks.remove(id);
        return millis;
    }

    private static void logBenchmark(Benchmark benchmark, boolean show) {
        String log = "Benchmark " + benchmark.name + " finished after " + benchmark.time + " milliseconds.";
        if (show) {
            System.out.println(log);
        }
        benchmarkLog.add(log);
    }

    public static void showBenchmarkLog() {
        if (benchmarkLog.isEmpty()) {
            System.out.println("BenchmarkLog is Empty");
        } else {
            System.out.println("------------BENCHMARK LOG------------");
            for (String benchmark : benchmarkLog) {
                System.out.println(benchmark);
            }
        }
    }

    private static class Benchmark {
        private final long startTime;
        private long time;
        private final String name;

        Benchmark(String name) {
            startTime = System.currentTimeMillis();
            this.name = name;
        }

        long endBenchmark() {
            time = System.currentTimeMillis() - startTime;
            logBenchmark(this, true);
            return System.currentTimeMillis() - startTime;
        }
    }
}

