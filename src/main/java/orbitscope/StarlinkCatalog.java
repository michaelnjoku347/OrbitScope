package orbitscope;

import java.nio.file.Path;

public class StarlinkCatalog {
    public static void main(String[] args) throws Exception {
        SatelliteCatalog catalog;
        String source;

        if (args.length == 0 || "--fetch".equals(args[0])) {
            source = TleFetcher.STARLINK_TLE_URL;
            String tleText = new TleFetcher().fetchStarlinkTles();
            catalog = TleParser.parse(tleText);
        } else if ("--file".equals(args[0]) && args.length >= 2) {
            Path path = Path.of(args[1]);
            source = path.toString();
            catalog = TleParser.readFile(path);
        } else {
            System.err.println("Usage:");
            System.err.println("  java orbitscope.StarlinkCatalog --fetch");
            System.err.println("  java orbitscope.StarlinkCatalog --file data/sample-starlink.tle");
            System.exit(1);
            return;
        }

        System.out.println("Source: " + source);
        for (Satellite satellite : catalog.all()) {
            System.out.println(satellite.getName());
        }
        System.out.println();
        System.out.println("count: " + catalog.size());
    }
}
