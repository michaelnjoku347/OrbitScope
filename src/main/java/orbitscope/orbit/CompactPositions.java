package orbitscope.orbit;

import java.time.Instant;

/** The skinny JSON the globe polls every second. */
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
