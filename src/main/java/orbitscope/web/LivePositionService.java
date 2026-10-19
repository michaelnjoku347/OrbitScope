package orbitscope.web;

import orbitscope.orbit.CompactPositions;
import orbitscope.orbit.PositionCalculator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class LivePositionService {
    private final CatalogService catalog;
    private final PositionCalculator calculator = new PositionCalculator();
    private volatile CompactPositions latest;

    public LivePositionService(CatalogService catalog) {
        this.catalog = catalog;
    }

    @Scheduled(fixedDelayString = "${orbitscope.live-positions-ms:1000}")
    public void refresh() {
        latest = calculator.compact(catalog.catalog().all(), Instant.now(), true);
    }

    public CompactPositions latest() {
        CompactPositions snapshot = latest;
        if (snapshot == null) {
            refresh();
            snapshot = latest;
        }
        return snapshot;
    }

    public PositionCalculator calculator() {
        return calculator;
    }
}
