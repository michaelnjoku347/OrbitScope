package orbitscope.orbit;

import orbitscope.Satellite;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
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

/**
 * Milestone 3: use Orekit's SGP4/SDP4 propagator to turn TLE lines into
 * latitude, longitude, and altitude at a chosen time.
 */
public class PositionCalculator {
    public SatellitePosition positionNow(Satellite satellite) {
        return position(satellite, Instant.now());
    }

    public SatellitePosition position(Satellite satellite, Instant when) {
        OrekitSupport.ensureLoaded();
        if (!satellite.hasTle()) {
            throw new IllegalArgumentException(satellite.getName() + " has no TLE lines");
        }

        String line1 = padTleLine(satellite.getTleLine1());
        String line2 = padTleLine(satellite.getTleLine2());
        if (!TLE.isFormatOK(line1, line2)) {
            throw new IllegalArgumentException("Invalid TLE for " + satellite.getName());
        }

        TLE tle = new TLE(line1, line2);
        TLEPropagator propagator = TLEPropagator.selectExtrapolator(tle);
        AbsoluteDate date = toAbsoluteDate(when);

        Frame itrf = FramesFactory.getITRF(IERSConventions.IERS_2010, true);
        OneAxisEllipsoid earth = new OneAxisEllipsoid(
                Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
                Constants.WGS84_EARTH_FLATTENING,
                itrf);

        SpacecraftState state = propagator.propagate(date);
        Vector3D cartesian = state.getPosition(itrf);
        GeodeticPoint point = earth.transform(cartesian, itrf, date);

        return new SatellitePosition(
                satellite.getNoradId(),
                satellite.getName(),
                when,
                FastMath.toDegrees(point.getLatitude()),
                FastMath.toDegrees(point.getLongitude()),
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

    static AbsoluteDate toAbsoluteDate(Instant when) {
        return new AbsoluteDate(Date.from(when), TimeScalesFactory.getUTC());
    }

    static String padTleLine(String line) {
        String trimmed = line.stripTrailing();
        if (trimmed.length() >= 69) {
            return trimmed.substring(0, 69);
        }
        return String.format("%-69s", trimmed);
    }
}
