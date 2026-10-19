package orbitscope;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrbitScopeTest {
    @Test
    void warmupStats() {
        SatelliteCatalog catalog = Warmup.sampleCatalog();
        Satellite highest = catalog.findHighest();
        assertEquals("STARLINK-1039", highest.getName());
        assertEquals(453.5, highest.getAltitudeKm(), 0.0001);
        assertEquals(406.5, catalog.averageAltitude(), 0.0001);
    }

    @Test
    void tleFile() throws Exception {
        Path sample = Path.of("data/sample-starlink.tle");
        SatelliteCatalog catalog = TleParser.readFile(sample);
        assertEquals(3, catalog.size());
        assertEquals("STARLINK-1008", catalog.all().get(0).getName());
        assertEquals(44714, catalog.all().get(0).getNoradId());
        assertEquals("STARLINK-1012", catalog.all().get(1).getName());
        assertEquals("STARLINK-1017", catalog.all().get(2).getName());
        assertTrue(catalog.all().get(0).getTleLine1().startsWith("1 "));
        assertTrue(catalog.all().get(0).getTleLine2().startsWith("2 "));
        assertEquals("STARLINK-1012", catalog.findById(44718).getName());
    }

    @Test
    void ignoresBlankLines() {
        String tle = """
                STARLINK-1008
                1 44714U 19074B   26292.10353694  .00048057  00000+0  43854-3 0  9993
                2 44714  53.1443 186.7069 0002755 134.2207 225.9036 15.67409625383553

                STARLINK-1012
                1 44718U 19074F   26292.27844111  .00044184  00000+0  39906-3 0  9994
                2 44718  53.1470 186.0207 0005630  93.8693 266.2967 15.67688618383572
                """;
        SatelliteCatalog catalog = TleParser.parse(tle);
        assertEquals(2, catalog.size());
        assertEquals("STARLINK-1008", catalog.all().get(0).getName());
        assertEquals("STARLINK-1012", catalog.all().get(1).getName());
    }

    @Test
    void issSampleHasNoradId() throws Exception {
        SatelliteCatalog catalog = TleParser.readFile(Path.of("data/sample-iss.tle"));
        Satellite iss = catalog.findById(25544);
        assertEquals("ISS (ZARYA)", iss.getName().trim());
        assertTrue(iss.hasTle());
        assertEquals("ISS", iss.getGroup());
    }

    @Test
    void groupsStarlinkAndGps() throws Exception {
        assertEquals("STARLINK", TleParser.readFile(Path.of("data/sample-starlink.tle")).all().get(0).getGroup());
        assertEquals("GPS", TleParser.readFile(Path.of("data/sample-gps.tle")).all().get(0).getGroup());
    }
}
