package orbitscope.orbit;

import orbitscope.Satellite;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.propagation.analytical.tle.TLEPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.IntStream;

public class PositionCalculator {
    private Frame itrf;
    private OneAxisEllipsoid earth;

    public SatellitePosition positionNow(Satellite satellite) {
        return position(satellite, Instant.now());
    }

    public SatellitePosition position(Satellite satellite, Instant when) {
        ensureEarth();
        if (!satellite.hasTle()) {
            throw new IllegalArgumentException(satellite.getName() + " has no TLE lines");
        }

        TLEPropagator propagator = propagatorFor(satellite);
        AbsoluteDate date = toAbsoluteDate(when);
        SpacecraftState state = propagator.propagate(date);
        Vector3D xyz = state.getPosition(itrf);
        GeodeticPoint point = earth.transform(xyz, itrf, date);

        return new SatellitePosition(
                satellite.getNoradId(),
                satellite.getName(),
                when,
                Math.toDegrees(point.getLatitude()),
                Math.toDegrees(point.getLongitude()),
                point.getAltitude() / 1000.0
        );
    }

    public List<SatellitePosition> positions(Iterable<Satellite> satellites, Instant when) {
        List<SatellitePosition> result = new ArrayList<>();
        for (Satellite satellite : satellites) {
            if (satellite.hasTle()) {
                result.add(position(satellite, when));
            }
        }
        return result;
    }

    public CompactPositions compact(List<Satellite> satellites, Instant when) {
        return compact(satellites, when, true);
    }

    public CompactPositions compact(List<Satellite> satellites, Instant when, boolean parallel) {
        ensureEarth();
        List<Satellite> usable = new ArrayList<>();
        for (int i = 0; i < satellites.size(); i++) {
            Satellite satellite = satellites.get(i);
            if (satellite.hasTle()) {
                usable.add(satellite);
            }
        }

        int n = usable.size();
        int[] ids = new int[n];
        double[] latitude = new double[n];
        double[] longitude = new double[n];
        double[] altitudeKm = new double[n];
        String[] group = new String[n];
        AbsoluteDate date = toAbsoluteDate(when);

        if (parallel) {
            IntStream.range(0, n).parallel().forEach(i -> {
                fillOne(usable.get(i), date, ids, latitude, longitude, altitudeKm, group, i);
            });
        } else {
            for (int i = 0; i < n; i++) {
                fillOne(usable.get(i), date, ids, latitude, longitude, altitudeKm, group, i);
            }
        }

        return new CompactPositions(when, n, ids, latitude, longitude, altitudeKm, group);
    }

    private void fillOne(
            Satellite satellite,
            AbsoluteDate date,
            int[] ids,
            double[] latitude,
            double[] longitude,
            double[] altitudeKm,
            String[] group,
            int i
    ) {
        ids[i] = satellite.getNoradId();
        group[i] = satellite.getGroup();
        try {
            TLEPropagator propagator = propagatorFor(satellite);
            Vector3D xyz = propagator.propagate(date).getPosition(itrf);
            GeodeticPoint point = earth.transform(xyz, itrf, date);
            latitude[i] = Math.toDegrees(point.getLatitude());
            longitude[i] = Math.toDegrees(point.getLongitude());
            altitudeKm[i] = point.getAltitude() / 1000.0;
        } catch (RuntimeException e) {
            // leave zeros
        }
    }

    Frame itrf() {
        ensureEarth();
        return itrf;
    }

    OneAxisEllipsoid earth() {
        ensureEarth();
        return earth;
    }

    TLEPropagator propagatorFor(Satellite satellite) {
        String line1 = padTleLine(satellite.getTleLine1());
        String line2 = padTleLine(satellite.getTleLine2());
        if (!TLE.isFormatOK(line1, line2)) {
            throw new IllegalArgumentException("Invalid TLE for " + satellite.getName());
        }
        return TLEPropagator.selectExtrapolator(new TLE(line1, line2));
    }

    private void ensureEarth() {
        OrekitSupport.ensureLoaded();
        if (earth == null) {
            itrf = FramesFactory.getITRF(IERSConventions.IERS_2010, true);
            earth = new OneAxisEllipsoid(
                    Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
                    Constants.WGS84_EARTH_FLATTENING,
                    itrf);
        }
    }

    static AbsoluteDate toAbsoluteDate(Instant when) {
        return new AbsoluteDate(Date.from(when), TimeScalesFactory.getUTC());
    }

    static String padTleLine(String line) { // TLE lines are 69 chars
        String trimmed = line.stripTrailing();
        if (trimmed.length() >= 69) {
            return trimmed.substring(0, 69);
        }
        return String.format("%-69s", trimmed);
    }
}
