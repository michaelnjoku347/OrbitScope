package orbitscope.orbit;

import orbitscope.Satellite;
import orbitscope.SatelliteCatalog;
import orbitscope.TleParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionCalculatorTest {
    @BeforeAll
    static void loadOrekit() {
        OrekitSupport.ensureLoaded();
    }

    @Test
    void issStaysInItsInclinationBand() throws Exception {
        Satellite iss = TleParser.readFile(Path.of("data/sample-iss.tle")).findById(25544);
        SatellitePosition position = new PositionCalculator().position(iss, Instant.parse("2026-10-19T14:00:00Z"));

        assertEquals(25544, position.id());
        assertTrue(position.latitude() >= -52 && position.latitude() <= 52,
                "ISS latitude should stay within ~51.6 deg inclination, was " + position.latitude());
        assertTrue(position.longitude() >= -180 && position.longitude() <= 180);
        assertTrue(position.altitudeKm() > 350 && position.altitudeKm() < 500,
                "ISS altitude should be LEO, was " + position.altitudeKm());
    }

    @Test
    void futureTimeMovesTheIss() throws Exception {
        Satellite iss = TleParser.readFile(Path.of("data/sample-iss.tle")).findById(25544);
        PositionCalculator calculator = new PositionCalculator();
        Instant now = Instant.parse("2026-10-19T14:00:00Z");
        SatellitePosition current = calculator.position(iss, now);
        SatellitePosition later = calculator.position(iss, now.plusSeconds(90 * 60));

        double groundKm = orbitscope.IssCheck.haversineKm(
                current.latitude(), current.longitude(),
                later.latitude(), later.longitude());
        assertTrue(groundKm > 1000, "90 minutes later the ISS should have moved, distance=" + groundKm);
    }

    @Test
    void computesEverySatelliteInTheSampleFile() throws Exception {
        SatelliteCatalog catalog = TleParser.readFile(Path.of("data/sample-starlink.tle"));
        List<SatellitePosition> positions = new PositionCalculator()
                .positions(catalog.all(), Instant.parse("2026-10-19T14:00:00Z"));
        assertEquals(3, positions.size());
        for (SatellitePosition position : positions) {
            assertTrue(position.altitudeKm() > 200 && position.altitudeKm() < 700,
                    position.name() + " altitude " + position.altitudeKm());
        }
    }
}
