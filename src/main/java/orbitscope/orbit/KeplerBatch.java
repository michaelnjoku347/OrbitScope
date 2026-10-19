package orbitscope.orbit;

import orbitscope.Satellite;
import org.hipparchus.util.FastMath;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Cheap Kepler step over arrays. Not as accurate as Orekit.
 * Wrote it this way so I can try TornadoVM later (needs a GPU I don't have).
 */
public class KeplerBatch {
    private static final double MU = 3.986004418e14;
    private static final double RE = 6378137.0;

    private final int n;
    private final int[] ids;
    private final double[] epochUnix;
    private final double[] meanMotion;
    private final double[] inclination;
    private final double[] raan;
    private final double[] argp;
    private final double[] meanAnomaly;
    private final double[] semiMajor;

    public KeplerBatch(List<Satellite> satellites) {
        List<Satellite> usable = new ArrayList<>();
        for (Satellite satellite : satellites) {
            if (satellite.hasTle()) {
                usable.add(satellite);
            }
        }
        n = usable.size();
        ids = new int[n];
        epochUnix = new double[n];
        meanMotion = new double[n];
        inclination = new double[n];
        raan = new double[n];
        argp = new double[n];
        meanAnomaly = new double[n];
        semiMajor = new double[n];

        OrekitSupport.ensureLoaded();
        for (int i = 0; i < n; i++) {
            Satellite satellite = usable.get(i);
            TLE tle = new TLE(
                    PositionCalculator.padTleLine(satellite.getTleLine1()),
                    PositionCalculator.padTleLine(satellite.getTleLine2()));
            ids[i] = satellite.getNoradId();
            AbsoluteDate unixEpoch = new AbsoluteDate(new Date(0L), TimeScalesFactory.getUTC());
            epochUnix[i] = tle.getDate().durationFrom(unixEpoch);
            meanMotion[i] = tle.getMeanMotion();
            inclination[i] = tle.getI();
            raan[i] = tle.getRaan();
            argp[i] = tle.getPerigeeArgument();
            meanAnomaly[i] = tle.getMeanAnomaly();
            semiMajor[i] = FastMath.cbrt(MU / (meanMotion[i] * meanMotion[i]));
        }
    }

    public int size() {
        return n;
    }

    public void propagate(double unixSeconds, float[] lat, float[] lon, float[] altKm, boolean parallel) {
        IntStream range = parallel ? IntStream.range(0, n).parallel() : IntStream.range(0, n);
        range.forEach(i -> {
            double dt = unixSeconds - epochUnix[i];
            double m = meanAnomaly[i] + meanMotion[i] * dt;
            m = FastMath.IEEEremainder(m, 2 * FastMath.PI);
            if (m < 0) {
                m += 2 * FastMath.PI;
            }
            double u = argp[i] + m;
            double cu = FastMath.cos(u);
            double su = FastMath.sin(u);
            double ci = FastMath.cos(inclination[i]);
            double si = FastMath.sin(inclination[i]);
            double cr = FastMath.cos(raan[i]);
            double sr = FastMath.sin(raan[i]);
            double r = semiMajor[i];
            double x = r * (cr * cu - sr * su * ci);
            double y = r * (sr * cu + cr * su * ci);
            double z = r * (su * si);

            double days = unixSeconds / 86400.0;
            double gmst = FastMath.IEEEremainder(4.894961212823672 + 6.30038809898496 * days, 2 * FastMath.PI);
            double cg = FastMath.cos(gmst);
            double sg = FastMath.sin(gmst);
            double xe = cg * x + sg * y;
            double ye = -sg * x + cg * y;
            double ze = z;

            lon[i] = (float) FastMath.toDegrees(FastMath.atan2(ye, xe));
            lat[i] = (float) FastMath.toDegrees(FastMath.atan2(ze, FastMath.sqrt(xe * xe + ye * ye)));
            altKm[i] = (float) ((FastMath.sqrt(xe * xe + ye * ye + ze * ze) - RE) / 1000.0);
        });
    }

    public void propagateSteps(double startUnix, double stepSeconds, int steps, boolean parallel) {
        float[] lat = new float[n];
        float[] lon = new float[n];
        float[] alt = new float[n];
        for (int s = 0; s < steps; s++) {
            propagate(startUnix + s * stepSeconds, lat, lon, alt, parallel);
        }
    }
}
