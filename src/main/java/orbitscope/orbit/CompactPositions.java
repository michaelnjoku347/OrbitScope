package orbitscope.orbit;

import java.time.Instant;

/** Arrays instead of a giant list of objects. Smaller json for the globe. */
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
