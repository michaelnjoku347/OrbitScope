package orbitscope.web;

import orbitscope.Satellite;
import orbitscope.SatelliteCatalog;
import orbitscope.TleFetcher;
import orbitscope.TleParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class CatalogService {
    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private final TleFetcher fetcher;
    private final boolean fetchOnStartup;
    private final String sampleTle;

    private volatile SatelliteCatalog catalog = new SatelliteCatalog();
    private volatile Instant updatedAt = Instant.EPOCH;

    public CatalogService(
            TleFetcher fetcher,
            @Value("${orbitscope.fetch-on-startup:true}") boolean fetchOnStartup,
            @Value("${orbitscope.sample-tle:data/sample-iss.tle}") String sampleTle
    ) {
        this.fetcher = fetcher;
        this.fetchOnStartup = fetchOnStartup;
        this.sampleTle = sampleTle;
    }

    @PostConstruct
    public void loadOnStartup() {
        try {
            if (fetchOnStartup) {
                refreshFromNetwork();
            } else {
                loadSample();
            }
        } catch (Exception e) {
            log.warn("Live TLE download failed ({}), falling back to {}", e.getMessage(), sampleTle);
            try {
                loadSample();
            } catch (Exception fallback) {
                throw new IllegalStateException("Could not load any TLE catalog", fallback);
            }
        }
    }

    @Scheduled(
            initialDelayString = "${orbitscope.refresh-ms:10800000}",
            fixedDelayString = "${orbitscope.refresh-ms:10800000}"
    )
    public void scheduledRefresh() {
        if (!fetchOnStartup) {
            return;
        }
        try {
            refreshFromNetwork();
        } catch (Exception e) {
            log.warn("Scheduled TLE refresh failed: {}", e.getMessage());
        }
    }

    public synchronized void refreshFromNetwork() throws Exception {
        log.info("grabbing new TLEs from CelesTrak");
        replace(TleParser.parse(fetcher.fetchCatalog()));
        log.info("catalog now has {} satellites", catalog.size());
    }

    public synchronized void loadSample() throws Exception {
        Path iss = Path.of(sampleTle);
        SatelliteCatalog loaded = TleParser.readFile(iss);
        for (String extra : List.of("data/sample-starlink.tle", "data/sample-gps.tle")) {
            Path path = Path.of(extra);
            if (path.toFile().isFile()) {
                for (Satellite satellite : TleParser.readFile(path).all()) {
                    loaded.add(satellite);
                }
            }
        }
        replace(loaded);
    }

    public SatelliteCatalog catalog() {
        return catalog;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Satellite require(int id) {
        Satellite satellite = catalog.findById(id);
        if (satellite == null) {
            throw new NoSuchElementException("No satellite with NORAD id " + id);
        }
        return satellite;
    }

    private void replace(SatelliteCatalog next) {
        this.catalog = next;
        this.updatedAt = Instant.now();
    }
}
