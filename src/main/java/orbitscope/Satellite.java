package orbitscope;

/**
 * One satellite OrbitScope knows about.
 *
 * Milestone 1 uses name, altitude, and speed.
 * Milestone 2 also keeps the two TLE lines from CelesTrak when we have them.
 */
public class Satellite {
    private final String name;
    private final int noradId;
    private final double altitudeKm;
    private final double speedKmPerS;
    private final String tleLine1;
    private final String tleLine2;

    public Satellite(String name, double altitudeKm, double speedKmPerS) {
        this(name, 0, altitudeKm, speedKmPerS, null, null);
    }

    public Satellite(String name, int noradId, double altitudeKm, double speedKmPerS,
                     String tleLine1, String tleLine2) {
        this.name = name;
        this.noradId = noradId;
        this.altitudeKm = altitudeKm;
        this.speedKmPerS = speedKmPerS;
        this.tleLine1 = tleLine1;
        this.tleLine2 = tleLine2;
    }

    public static Satellite fromTle(String name, String line1, String line2) {
        return new Satellite(name.trim(), parseNoradId(line1), 0.0, 0.0, line1, line2);
    }

    /** Starlink, GPS, ISS, or OTHER — used by the globe filters. */
    public String getGroup() {
        String upper = name.toUpperCase();
        if (upper.contains("ISS") || upper.contains("ZARYA")) {
            return "ISS";
        }
        if (upper.contains("STARLINK")) {
            return "STARLINK";
        }
        if (upper.contains("GPS") || upper.contains("NAVSTAR")) {
            return "GPS";
        }
        return "OTHER";
    }

    static int parseNoradId(String line1) {
        if (line1 == null || line1.length() < 7) {
            return 0;
        }
        return Integer.parseInt(line1.substring(2, 7).trim());
    }

    public String getName() {
        return name;
    }

    public int getNoradId() {
        return noradId;
    }

    public double getAltitudeKm() {
        return altitudeKm;
    }

    public double getSpeedKmPerS() {
        return speedKmPerS;
    }

    public String getTleLine1() {
        return tleLine1;
    }

    public String getTleLine2() {
        return tleLine2;
    }

    public boolean hasTle() {
        return tleLine1 != null && tleLine2 != null;
    }

    @Override
    public String toString() {
        return name + " (altitude " + altitudeKm + " km, speed " + speedKmPerS + " km/s)";
    }
}
