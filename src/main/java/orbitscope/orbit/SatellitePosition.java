package orbitscope.orbit;

import java.time.Instant;

/** Where a satellite is at one instant, in geodetic coordinates. */
public record SatellitePosition(
        int id,
        String name,
        Instant time,
        double latitude,
        double longitude,
        double altitudeKm
) {
    @Override
    public String toString() {
        return String.format(
                "%s (%d) at %s  lat %+8.4f  lon %+9.4f  alt %7.1f km",
                name, id, time, latitude, longitude, altitudeKm);
    }
}
