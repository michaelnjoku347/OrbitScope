package orbitscope.orbit;

import java.time.Instant;

public record Pass(
        Instant start,
        Instant peak,
        Instant end,
        double maxElevationDeg
) {
}
