package orbitscope.orbit;

import java.time.Instant;

public record CompactPositions(
        Instant time,
        int count,
        int[] ids,
        double[] latitude,
        double[] longitude,
        double[] altitudeKm,
        String[] group
) {
}
