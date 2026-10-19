package orbitscope.orbit;

import orbitscope.Satellite;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.frames.Frame;
import org.orekit.frames.TopocentricFrame;
import org.orekit.propagation.analytical.tle.TLEPropagator;
import org.orekit.time.AbsoluteDate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class PassPredictor {
    private final PositionCalculator calculator;

    public PassPredictor() {
        this(new PositionCalculator());
    }

    public PassPredictor(PositionCalculator calculator) {
        this.calculator = calculator;
    }

    public List<Pass> predict(Satellite satellite, Observer observer, Instant from, Duration window) {
        return predict(satellite, observer, from, window, 10.0, 30.0);
    }

    public List<Pass> predict(
            Satellite satellite,
            Observer observer,
            Instant from,
            Duration window,
            double minElevationDeg,
            double stepSeconds
    ) {
        TLEPropagator propagator = calculator.propagatorFor(satellite);
        Frame itrf = calculator.itrf();
        OneAxisEllipsoid earth = calculator.earth();
        GeodeticPoint site = new GeodeticPoint(
                Math.toRadians(observer.latitudeDeg()),
                Math.toRadians(observer.longitudeDeg()),
                observer.altitudeM());
        TopocentricFrame topo = new TopocentricFrame(earth, site, observer.name());
        AbsoluteDate start = PositionCalculator.toAbsoluteDate(from);
        int steps = Math.max(2, (int) (window.toSeconds() / stepSeconds));
        double minEl = Math.toRadians(minElevationDeg);

        List<Pass> passes = new ArrayList<>();
        Instant passStart = null;
        Instant peakTime = null;
        double peakEl = Double.NEGATIVE_INFINITY;
        Instant lastVisible = null;

        for (int i = 0; i < steps; i++) {
            AbsoluteDate date = start.shiftedBy(i * stepSeconds);
            Vector3D position = propagator.propagate(date).getPosition(itrf);
            double elevation = topo.getElevation(position, itrf, date);
            Instant time = from.plusMillis((long) (i * stepSeconds * 1000.0));
            if (elevation >= minEl) {
                if (passStart == null) {
                    passStart = time;
                    peakTime = time;
                    peakEl = elevation;
                } else if (elevation > peakEl) {
                    peakEl = elevation;
                    peakTime = time;
                }
                lastVisible = time;
            } else if (passStart != null) {
                passes.add(new Pass(passStart, peakTime, lastVisible, Math.toDegrees(peakEl)));
                passStart = null;
                peakTime = null;
                peakEl = Double.NEGATIVE_INFINITY;
                lastVisible = null;
            }
        }
        if (passStart != null) {
            passes.add(new Pass(passStart, peakTime, lastVisible, Math.toDegrees(peakEl)));
        }
        return passes;
    }
}
