package orbitscope.orbit;

import java.time.Instant;

/** Tight JSON payload so the globe can refresh thousands of dots every second. */
public record CompactPositions(
        Instant time,
        int count,
        int[] ids,
        float[] latitude,
        float[] longitude,
        float[] altitudeKm,
        String[] group
) {
}
