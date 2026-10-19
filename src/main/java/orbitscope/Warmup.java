package orbitscope;

/** Milestone 1: arrays, loops, a Satellite class, ArrayList, and helper methods. */
public class Warmup {
    public static void main(String[] args) {
        String[] names = {
            "STARLINK-1008",
            "STARLINK-1012",
            "STARLINK-1017",
            "STARLINK-1039"
        };

        System.out.println("Satellite names:");
        for (int i = 0; i < names.length; i++) {
            System.out.println("  " + (i + 1) + ". " + names[i]);
        }

        SatelliteCatalog catalog = sampleCatalog();
        System.out.println();
        System.out.println("Satellite objects:");
        for (Satellite satellite : catalog.all()) {
            System.out.println("  " + satellite);
        }

        Satellite highest = catalog.findHighest();
        System.out.println();
        System.out.println("Highest satellite: " + highest.getName()
                + " at " + highest.getAltitudeKm() + " km");
        System.out.println("Average altitude: " + catalog.averageAltitude() + " km");
    }

    static SatelliteCatalog sampleCatalog() {
        SatelliteCatalog catalog = new SatelliteCatalog();
        catalog.add(new Satellite("STARLINK-1008", 366.0, 7.67));
        catalog.add(new Satellite("STARLINK-1012", 365.5, 7.67));
        catalog.add(new Satellite("STARLINK-1017", 441.0, 7.64));
        catalog.add(new Satellite("STARLINK-1039", 453.5, 7.63));
        return catalog;
    }
}
