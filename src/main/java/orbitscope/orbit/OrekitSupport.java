package orbitscope.orbit;

import org.orekit.data.DataContext;
import org.orekit.data.DataProvidersManager;
import org.orekit.data.DirectoryCrawler;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Orekit needs leap seconds and Earth-orientation files before it can turn a
 * TLE into latitude/longitude. This class loads {@code orekit-data/} or
 * downloads it from the Orekit forge the first time you run the program.
 */
public final class OrekitSupport {
    public static final String DATA_ZIP_URL =
            "https://gitlab.orekit.org/orekit/orekit-data/-/archive/main/orekit-data-main.zip";

    private static final Object LOCK = new Object();
    private static boolean loaded;

    private OrekitSupport() {
    }

    public static void ensureLoaded() {
        synchronized (LOCK) {
            if (loaded) {
                return;
            }
            Path dataDir = dataDirectory();
            try {
                if (!looksValid(dataDir)) {
                    downloadAndExtract(dataDir);
                }
                DataProvidersManager manager = DataContext.getDefault().getDataProvidersManager();
                manager.addProvider(new DirectoryCrawler(dataDir.toFile()));
                loaded = true;
            } catch (IOException e) {
                throw new UncheckedIOException("Could not load Orekit data from " + dataDir, e);
            }
        }
    }

    public static Path dataDirectory() {
        String configured = System.getProperty("orbitscope.orekit-data", "orekit-data");
        return Path.of(configured).toAbsolutePath();
    }

    private static boolean looksValid(Path dataDir) {
        return Files.isRegularFile(dataDir.resolve("tai-utc.dat"))
                || Files.isRegularFile(dataDir.resolve("UTC-TAI.history"));
    }

    private static void downloadAndExtract(Path dataDir) throws IOException {
        System.err.println("Downloading Orekit data from " + DATA_ZIP_URL);
        Files.createDirectories(dataDir.getParent() == null ? Path.of(".") : dataDir.getParent());
        Path tmp = Files.createTempDirectory("orekit-data-");
        try (InputStream in = URI.create(DATA_ZIP_URL).toURL().openStream();
             ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path destination = safeResolve(tmp, stripTopFolder(entry.getName()));
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(zip, destination);
                }
            }
        }
        Path extracted = tmp;
        if (!looksValid(extracted)) {
            try (var stream = Files.list(tmp)) {
                extracted = stream.filter(Files::isDirectory).findFirst().orElse(tmp);
            }
        }
        if (Files.exists(dataDir)) {
            deleteRecursively(dataDir);
        }
        Files.move(extracted, dataDir);
    }

    private static String stripTopFolder(String name) {
        int slash = name.indexOf('/');
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    private static Path safeResolve(Path root, String relative) throws IOException {
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            throw new IOException("Refusing to extract " + relative + " outside " + root);
        }
        return resolved;
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
    }
}
