package orbitscope.web;

import orbitscope.Satellite;
import orbitscope.orbit.PositionCalculator;
import orbitscope.orbit.SatellitePosition;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class SatelliteController {
    private final CatalogService catalog;
    private final PositionCalculator calculator = new PositionCalculator();

    public SatelliteController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/")
    public Map<String, Object> index() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "OrbitScope");
        body.put("satelliteCount", catalog.catalog().size());
        body.put("updatedAt", catalog.updatedAt());
        body.put("endpoints", List.of(
                "/satellites",
                "/satellites/{id}/position",
                "/satellites/{id}/position?at=2026-10-19T18:00:00Z",
                "/satellites/positions"
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
        return calculator.position(catalog.require(id), when);
    }

    @GetMapping("/satellites/positions")
    public Map<String, Object> positions(@RequestParam(required = false) Instant at) {
        Instant when = at == null ? Instant.now() : at;
        List<SatellitePosition> positions = calculator.positions(catalog.catalog().all(), when);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("count", positions.size());
        body.put("time", when);
        body.put("positions", positions);
        return body;
    }
}
