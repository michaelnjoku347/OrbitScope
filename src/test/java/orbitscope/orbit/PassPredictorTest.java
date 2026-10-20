package orbitscope.orbit;

import orbitscope.Satellite;
import orbitscope.TleParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassPredictorTest {
    @BeforeAll
    static void loadOrekit() {
        OrekitSupport.ensureLoaded();
    }

    @Test
    void findsIssPassesOverADay() throws Exception {
        Satellite iss = TleParser.readFile(Path.of("data/sample-iss.tle")).findById(25544);
        List<Pass> passes = new PassPredictor().predict(
                iss,
                Observer.NEW_YORK,
                Instant.parse("2026-10-19T00:00:00Z"),
                Duration.ofHours(24));
        assertFalse(passes.isEmpty(), "ISS should rise over New York at least once a day");
        for (Pass pass : passes) {
            assertTrue(pass.maxElevationDeg() >= 10.0);
            assertTrue(pass.end().isAfter(pass.start()) || pass.end().equals(pass.start()));
        }
    }
}
