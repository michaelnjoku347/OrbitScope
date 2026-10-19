package orbitscope;

import orbitscope.orbit.KeplerBatch;
import orbitscope.orbit.OrekitSupport;
import orbitscope.orbit.PositionCalculator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Milestone 7: time the heavy loop on one core, then across every core, then
 * on the simplified Kepler kernel that a GPU would run.
 */
public class Benchmark {
    public static void main(String[] args) throws Exception {
        OrekitSupport.ensureLoaded();
        SatelliteCatalog catalog = loadCatalog();
        List<Satellite> satellites = new ArrayList<>(catalog.all());
        int steps = args.length > 0 ? Integer.parseInt(args[0]) : 20;
        double stepSeconds = 30.0;
        Instant start = Instant.parse("2026-10-19T14:00:00Z");

        System.out.println("Satellites: " + satellites.size());
        System.out.println("Steps:      " + steps + " x " + (int) stepSeconds + "s");
        System.out.println("Cores:      " + Runtime.getRuntime().availableProcessors());
        System.out.println();

        PositionCalculator calculator = new PositionCalculator();
        long orekitSeq = time(() -> {
            for (int s = 0; s < steps; s++) {
                calculator.compact(satellites, start.plusSeconds((long) (s * stepSeconds)), false);
            }
        });
        long orekitPar = time(() -> {
            for (int s = 0; s < steps; s++) {
                calculator.compact(satellites, start.plusSeconds((long) (s * stepSeconds)), true);
            }
        });

        KeplerBatch kepler = new KeplerBatch(satellites);
        double startUnix = start.getEpochSecond();
        long keplerSeq = time(() -> kepler.propagateSteps(startUnix, stepSeconds, steps, false));
        long keplerPar = time(() -> kepler.propagateSteps(startUnix, stepSeconds, steps, true));

        Result result = new Result(satellites.size(), steps, orekitSeq, orekitPar, keplerSeq, keplerPar);
        System.out.println(result.report());
        Path out = Path.of("docs/benchmark.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, result.markdown());
        Files.writeString(Path.of("docs/benchmark.svg"), result.svg());
        System.out.println("Wrote docs/benchmark.md and docs/benchmark.svg");
    }

    private static SatelliteCatalog loadCatalog() throws Exception {
        SatelliteCatalog catalog = new SatelliteCatalog();
        for (String file : List.of("data/sample-iss.tle", "data/sample-gps.tle", "data/sample-starlink.tle")) {
            Path path = Path.of(file);
            if (Files.isRegularFile(path)) {
                for (Satellite satellite : TleParser.readFile(path).all()) {
                    catalog.add(satellite);
                }
            }
        }
        // Repeat the sample set so the timed loop is large enough to measure.
        List<Satellite> seed = new ArrayList<>(catalog.all());
        while (catalog.size() < 2048) {
            for (Satellite satellite : seed) {
                catalog.add(satellite);
                if (catalog.size() >= 2048) {
                    break;
                }
            }
        }
        return catalog;
    }

    private static long time(Runnable work) {
        work.run(); // warmup
        long start = System.nanoTime();
        work.run();
        return (System.nanoTime() - start) / 1_000_000;
    }

    record Result(int satellites, int steps, long orekitSeqMs, long orekitParMs, long keplerSeqMs, long keplerParMs) {
        double orekitSpeedup() {
            return orekitParMs == 0 ? 0 : (double) orekitSeqMs / orekitParMs;
        }

        double keplerVsOrekit() {
            return keplerParMs == 0 ? 0 : (double) orekitSeqMs / keplerParMs;
        }

        String report() {
            return """
                    Orekit SGP4 sequential : %d ms
                    Orekit SGP4 parallel   : %d ms  (%.2fx vs sequential)
                    Kepler sequential      : %d ms
                    Kepler parallel        : %d ms  (%.2fx vs Orekit sequential)
                    """.formatted(orekitSeqMs, orekitParMs, orekitSpeedup(), keplerSeqMs, keplerParMs, keplerVsOrekit());
        }

        String markdown() {
            return """
                    # OrbitScope benchmark

                    Workload: **%d** satellites × **%d** time steps (30 s apart) on **%d** CPU cores.
                    No NVIDIA GPU was attached, so the "GPU-shaped" path is a Kepler two-body kernel
                    over plain arrays, run with Java parallel streams (the same map a CUDA/TornadoVM
                    kernel would launch).

                    | Path | Time | Speedup vs Orekit sequential |
                    | --- | ---: | ---: |
                    | Orekit SGP4, 1 core | %d ms | 1.00× |
                    | Orekit SGP4, all cores | %d ms | %.2f× |
                    | Kepler arrays, 1 core | %d ms | %.2f× |
                    | Kepler arrays, all cores | %d ms | %.2f× |

                    The Kepler rewrite is the heavy-loop replacement. Hook it to TornadoVM or CUDA/JNI
                    on a machine with a GPU to go further; the kernel is already a per-satellite map
                    with no shared mutable state.
                    """.formatted(
                    satellites, steps, Runtime.getRuntime().availableProcessors(),
                    orekitSeqMs, orekitParMs, orekitSpeedup(),
                    keplerSeqMs, (double) orekitSeqMs / Math.max(1, keplerSeqMs),
                    keplerParMs, keplerVsOrekit());
        }

        String svg() {
            long max = Math.max(1, Math.max(orekitSeqMs, Math.max(orekitParMs, Math.max(keplerSeqMs, keplerParMs))));
            return """
                    <svg xmlns="http://www.w3.org/2000/svg" width="720" height="280" viewBox="0 0 720 280">
                      <rect width="720" height="280" fill="#070b14"/>
                      <text x="24" y="36" fill="#e8eef7" font-family="ui-sans-serif, system-ui" font-size="18">OrbitScope propagation benchmark</text>
                      <text x="24" y="58" fill="#8b9bb4" font-family="ui-sans-serif, system-ui" font-size="12">%d sats × %d steps · %d cores</text>
                      %s
                      %s
                      %s
                      %s
                    </svg>
                    """.formatted(
                    satellites, steps, Runtime.getRuntime().availableProcessors(),
                    bar(0, "Orekit sequential", orekitSeqMs, max, "#f5c16c"),
                    bar(1, "Orekit parallel", orekitParMs, max, "#3ee0c3"),
                    bar(2, "Kepler sequential", keplerSeqMs, max, "#7aa2f7"),
                    bar(3, "Kepler parallel", keplerParMs, max, "#8be38b"));
        }

        private static String bar(int row, String label, long ms, long max, String color) {
            double width = 420.0 * ms / max;
            int y = 88 + row * 44;
            return """
                    <text x="24" y="%d" fill="#c5d0e0" font-family="ui-sans-serif, system-ui" font-size="13">%s</text>
                    <rect x="200" y="%d" width="%.1f" height="22" rx="4" fill="%s"/>
                    <text x="%.1f" y="%d" fill="#e8eef7" font-family="ui-sans-serif, system-ui" font-size="12">%d ms</text>
                    """.formatted(y + 16, label, y, width, color, 212 + width, y + 16, ms);
        }
    }
}
