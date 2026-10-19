package orbitscope.orbit;

import orbitscope.Satellite;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Simpler (worse) orbit step using arrays.
 * I wrote this because Orekit objects can't go on a GPU.
 * The globe does NOT use this. It's only for the timing test.
 */
public class KeplerBatch {
    // I looked these up. MU = earth gravity, RE = earth radius in meters
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
        for (int i = 0; i < satellites.size(); i++) {
            if (satellites.get(i).hasTle()) {
                usable.add(satellites.get(i));
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
            semiMajor[i] = Math.cbrt(MU / (meanMotion[i] * meanMotion[i]));
        }
    }

    public int size() {
        return n;
    }

    public void propagate(double unixSeconds, float[] lat, float[] lon, float[] altKm, boolean parallel) {
        if (parallel) {
            IntStream.range(0, n).parallel().forEach(i -> {
                stepOne(i, unixSeconds, lat, lon, altKm);
            });
        } else {
            for (int i = 0; i < n; i++) {
                stepOne(i, unixSeconds, lat, lon, altKm);
            }
        }
    }

    // I translated this from wikipedia / a blog. probably missing a lot.
    private void stepOne(int i, double unixSeconds, float[] lat, float[] lon, float[] altKm) {
        double dt = unixSeconds - epochUnix[i];
        double m = meanAnomaly[i] + meanMotion[i] * dt;
        m = Math.IEEEremainder(m, 2 * Math.PI);
        if (m < 0) {
            m += 2 * Math.PI;
        }
        double u = argp[i] + m;
        double cu = Math.cos(u);
        double su = Math.sin(u);
        double ci = Math.cos(inclination[i]);
        double si = Math.sin(inclination[i]);
        double cr = Math.cos(raan[i]);
        double sr = Math.sin(raan[i]);
        double r = semiMajor[i];
        double x = r * (cr * cu - sr * su * ci);
        double y = r * (sr * cu + cr * su * ci);
        double z = r * (su * si);

        // rotate from space coords to earth coords. numbers from a gmst formula I copied.
        double days = unixSeconds / 86400.0;
        double gmst = Math.IEEEremainder(4.894961212823672 + 6.30038809898496 * days, 2 * Math.PI);
        double cg = Math.cos(gmst);
        double sg = Math.sin(gmst);
        double xe = cg * x + sg * y;
        double ye = -sg * x + cg * y;
        double ze = z;

        lon[i] = (float) Math.toDegrees(Math.atan2(ye, xe));
        lat[i] = (float) Math.toDegrees(Math.atan2(ze, Math.sqrt(xe * xe + ye * ye)));
        altKm[i] = (float) ((Math.sqrt(xe * xe + ye * ye + ze * ze) - RE) / 1000.0);
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
