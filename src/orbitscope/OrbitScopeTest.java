package orbitscope;

import java.nio.file.Path;

/** Tiny checks so we can run the first two milestones without extra libraries. */
public class OrbitScopeTest {
    public static void main(String[] args) throws Exception {
        testWarmupStats();
        testTleFile();
        testIgnoresBlankLines();
        System.out.println("All tests passed.");
    }

    private static void testWarmupStats() {
        SatelliteCatalog catalog = Warmup.sampleCatalog();
        Satellite highest = catalog.findHighest();
        assertEqual("STARLINK-1039", highest.getName());
        assertEqual(453.5, highest.getAltitudeKm(), 0.0001);
        assertEqual(406.5, catalog.averageAltitude(), 0.0001);
    }

    private static void testTleFile() throws Exception {
        Path sample = Path.of("data/sample-starlink.tle");
        SatelliteCatalog catalog = TleParser.readFile(sample);
        assertEqual(3, catalog.size());
        assertEqual("STARLINK-1008", catalog.all().get(0).getName());
        assertEqual("STARLINK-1012", catalog.all().get(1).getName());
        assertEqual("STARLINK-1017", catalog.all().get(2).getName());
        assertTrue(catalog.all().get(0).getTleLine1().startsWith("1 "));
        assertTrue(catalog.all().get(0).getTleLine2().startsWith("2 "));
    }

    private static void testIgnoresBlankLines() {
        String tle = """
                STARLINK-1008
                1 44714U 19074B   26292.10353694  .00048057  00000+0  43854-3 0  9993
                2 44714  53.1443 186.7069 0002755 134.2207 225.9036 15.67409625383553

                STARLINK-1012
                1 44718U 19074F   26292.27844111  .00044184  00000+0  39906-3 0  9994
                2 44718  53.1470 186.0207 0005630  93.8693 266.2967 15.67688618383572
                """;
        SatelliteCatalog catalog = TleParser.parse(tle);
        assertEqual(2, catalog.size());
        assertEqual("STARLINK-1008", catalog.all().get(0).getName());
        assertEqual("STARLINK-1012", catalog.all().get(1).getName());
    }

    private static void assertTrue(boolean value) {
        if (!value) {
            throw new AssertionError("expected true");
        }
    }

    private static void assertEqual(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }

    private static void assertEqual(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected \"" + expected + "\" but was \"" + actual + "\"");
        }
    }

    private static void assertEqual(double expected, double actual, double delta) {
        if (Math.abs(expected - actual) > delta) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }
}
