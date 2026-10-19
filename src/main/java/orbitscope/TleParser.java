package orbitscope;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * CelesTrak gives you name / line1 / line2, over and over.
 * I skip blank lines then take groups of 3.
 */
public class TleParser {
    public static SatelliteCatalog readFile(Path path) throws IOException {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append('\n');
            }
        }
        return parse(text.toString());
    }

    public static SatelliteCatalog parse(String text) {
        List<String> lines = new ArrayList<>();
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }

        SatelliteCatalog catalog = new SatelliteCatalog();
        for (int i = 0; i + 2 < lines.size(); i += 3) {
            String name = lines.get(i);
            String line1 = lines.get(i + 1);
            String line2 = lines.get(i + 2);

            if (!line1.startsWith("1") || !line2.startsWith("2")) {
                throw new IllegalArgumentException(
                        "Expected TLE lines 1 and 2 after \"" + name + "\", "
                                + "but got:\n" + line1 + "\n" + line2);
            }
            catalog.add(Satellite.fromTle(name, line1, line2));
        }
        return catalog;
    }
}
