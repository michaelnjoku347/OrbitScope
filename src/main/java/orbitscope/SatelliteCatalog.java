package orbitscope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A list of satellites plus the Milestone 1 helper methods:
 * find the highest satellite, and average altitude.
 */
public class SatelliteCatalog {
    private final List<Satellite> satellites = new ArrayList<>();

    public void add(Satellite satellite) {
        satellites.add(satellite);
    }

    public int size() {
        return satellites.size();
    }

    public List<Satellite> all() {
        return Collections.unmodifiableList(satellites);
    }

    public Satellite findById(int noradId) {
        for (Satellite satellite : satellites) {
            if (satellite.getNoradId() == noradId) {
                return satellite;
            }
        }
        return null;
    }

    public Satellite findHighest() {
        if (satellites.isEmpty()) {
            return null;
        }

        Satellite highest = satellites.get(0);
        for (int i = 1; i < satellites.size(); i++) {
            Satellite current = satellites.get(i);
            if (current.getAltitudeKm() > highest.getAltitudeKm()) {
                highest = current;
            }
        }
        return highest;
    }

    public double averageAltitude() {
        if (satellites.isEmpty()) {
            return 0.0;
        }

        double total = 0.0;
        for (Satellite satellite : satellites) {
            total += satellite.getAltitudeKm();
        }
        return total / satellites.size();
    }
}
