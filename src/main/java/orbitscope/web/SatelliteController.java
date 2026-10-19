package orbitscope.web;

import orbitscope.Satellite;
import orbitscope.orbit.CompactPositions;
import orbitscope.orbit.Observer;
import orbitscope.orbit.Pass;
import orbitscope.orbit.PassPredictor;
import orbitscope.orbit.SatellitePosition;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class SatelliteController {
    private final CatalogService catalog;
    private final LivePositionService livePositions;
    private final PassPredictor passes = new PassPredictor();

    public SatelliteController(CatalogService catalog, LivePositionService livePositions) {
        this.catalog = catalog;
        this.livePositions = livePositions;
    }

    @GetMapping("/api")
    public Map<String, Object> index() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "OrbitScope");
        body.put("note", "java backend for the globe. observer defaults to freeport.");
        body.put("satelliteCount", catalog.catalog().size());
        body.put("updatedAt", catalog.updatedAt());
        body.put("observer", Observer.FREEPORT.name());
        body.put("endpoints", List.of(
                "/satellites",
                "/satellites/{id}/position",
                "/satellites/{id}/position?at=2026-10-19T18:00:00Z",
                "/satellites/{id}/passes",
                "/satellites/positions",
                "/satellites/positions/compact"
        ));
        return body;
    }

    @GetMapping("/satellites")
    public Map<String, Object> list() {
        List<Map<String, Object>> satellites = new ArrayList<>();
        for (Satellite satellite : catalog.catalog().all()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", satellite.getNoradId());
            row.put("name", satellite.getName());
            row.put("group", satellite.getGroup());
            satellites.add(row);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("count", satellites.size());
        body.put("updatedAt", catalog.updatedAt());
        body.put("satellites", satellites);
        return body;
    }

    @GetMapping("/satellites/{id}/position")
    public SatellitePosition position(
            @PathVariable int id,
            @RequestParam(required = false) Instant at
    ) {
        Instant when = at == null ? Instant.now() : at;
        return livePositions.calculator().position(catalog.require(id), when);
    }

    @GetMapping("/satellites/{id}/passes")
    public Map<String, Object> passes(
            @PathVariable int id,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon,
            @RequestParam(defaultValue = "24") int hours
    ) {
        Observer observer = (lat != null && lon != null)
                ? new Observer("Custom", lat, lon, 10.0)
                : Observer.FREEPORT;
        Satellite satellite = catalog.require(id);
        List<Pass> predicted = passes.predict(
                satellite,
                observer,
                Instant.now(),
                Duration.ofHours(Math.max(1, Math.min(hours, 72))));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", satellite.getNoradId());
        body.put("name", satellite.getName());
        body.put("observer", Map.of(
                "name", observer.name(),
                "latitude", observer.latitudeDeg(),
                "longitude", observer.longitudeDeg()
        ));
        body.put("passes", predicted);
        return body;
    }

    @GetMapping("/satellites/positions")
    public Map<String, Object> positions(@RequestParam(required = false) Instant at) {
        Instant when = at == null ? Instant.now() : at;
        List<SatellitePosition> positions = livePositions.calculator()
                .positions(catalog.catalog().all(), when);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("count", positions.size());
        body.put("time", when);
        body.put("positions", positions);
        return body;
    }

    @GetMapping("/satellites/positions/compact")
    public CompactPositions compact() {
        return livePositions.latest();
    }
}
