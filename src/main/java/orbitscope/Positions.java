package orbitscope;

import orbitscope.orbit.PositionCalculator;
import orbitscope.orbit.SatellitePosition;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Dump positions. Default is now; `--future PT90M` is 90 minutes out.
 */
public class Positions {
    public static void main(String[] args) throws Exception {
        Arguments arguments = Arguments.parse(args);
        SatelliteCatalog catalog = load(arguments);
        Instant when = arguments.when();
        PositionCalculator calculator = new PositionCalculator();

        System.out.println("Computing " + catalog.size() + " positions at " + when);

        if (arguments.all) {
            List<SatellitePosition> positions = calculator.positions(catalog.all(), when);
            for (SatellitePosition position : positions) {
                System.out.println(position);
            }
            System.out.println();
            System.out.println("Positions: " + positions.size());
            return;
        }

        Satellite iss = catalog.findById(TleFetcher.ISS_NORAD_ID);
        if (iss != null) {
            System.out.println(calculator.position(iss, when));
        }

        int printed = 0;
        for (Satellite satellite : catalog.all()) {
            if (satellite.getNoradId() == TleFetcher.ISS_NORAD_ID) {
                continue;
            }
            System.out.println(calculator.position(satellite, when));
            printed++;
            if (printed >= arguments.limit) {
                break;
            }
        }
        System.out.println();
        System.out.println("Printed " + (printed + (iss == null ? 0 : 1))
                + " of " + catalog.size() + " (use --all to print every satellite)");
    }

    private static SatelliteCatalog load(Arguments arguments) throws Exception {
        if (arguments.file != null) {
            return TleParser.readFile(Path.of(arguments.file));
        }
        return TleParser.parse(new TleFetcher().fetchCatalog());
    }

    private record Arguments(String file, Instant when, int limit, boolean all) {
        static Arguments parse(String[] args) {
            String file = null;
            Instant when = Instant.now();
            int limit = 8;
            boolean all = false;
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--file" -> file = args[++i];
                    case "--at" -> when = Instant.parse(args[++i]);
                    case "--future" -> when = Instant.now().plus(Duration.parse(args[++i]));
                    case "--limit" -> limit = Integer.parseInt(args[++i]);
                    case "--all" -> all = true;
                    default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
                }
            }
            return new Arguments(file, when, limit, all);
        }
    }
}
