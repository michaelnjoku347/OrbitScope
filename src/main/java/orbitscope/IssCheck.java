package orbitscope;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import orbitscope.orbit.PositionCalculator;
import orbitscope.orbit.SatellitePosition;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

public class IssCheck {
    public static final String TRACKER_URL = "https://api.wheretheiss.at/v1/satellites/25544";

    public static void main(String[] args) throws Exception {
        Result result = run();
        System.out.println("Orekit:  " + result.ours());
        System.out.println("Tracker: " + result.theirs());
        System.out.printf(
                "Delta:   %.3f deg latitude, %.3f deg longitude, %.1f km altitude, %.1f km ground distance%n",
                result.latitudeDeltaDeg(),
                result.longitudeDeltaDeg(),
                result.altitudeDeltaKm(),
                result.groundDistanceKm());
        if (!result.matches()) {
            throw new IllegalStateException("way off from the public tracker");
        }
        System.out.println("ok, close enough");
    }

    static Result run() throws Exception {
        TrackerSample tracker = fetchTracker();
        String tleText = new TleFetcher().fetchIssTle();
        Satellite iss = TleParser.parse(tleText).findById(TleFetcher.ISS_NORAD_ID);
        if (iss == null) {
            throw new IllegalStateException("CelesTrak response did not include ISS (25544)");
        }

        SatellitePosition ours = new PositionCalculator().position(iss, tracker.time());
        return new Result(ours, tracker);
    }

    private static TrackerSample fetchTracker() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(TRACKER_URL))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "OrbitScope/0.1 (https://github.com/michaelnjoku347/OrbitScope)")
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Tracker HTTP " + response.statusCode());
        }
        JsonNode json = new ObjectMapper().readTree(response.body());
        Instant time = Instant.ofEpochSecond(json.get("timestamp").asLong());
        return new TrackerSample(
                time,
                json.get("latitude").asDouble(),
                json.get("longitude").asDouble(),
                json.get("altitude").asDouble()
        );
    }

    record TrackerSample(Instant time, double latitude, double longitude, double altitudeKm) {
    }

    record Result(SatellitePosition ours, TrackerSample theirs) {
        double latitudeDeltaDeg() {
            return Math.abs(ours.latitude() - theirs.latitude());
        }

        double longitudeDeltaDeg() {
            return wrapLongitude(ours.longitude() - theirs.longitude());
        }

        double altitudeDeltaKm() {
            return Math.abs(ours.altitudeKm() - theirs.altitudeKm());
        }

        double groundDistanceKm() {
            return haversineKm(ours.latitude(), ours.longitude(), theirs.latitude(), theirs.longitude());
        }

        boolean matches() {
            return latitudeDeltaDeg() <= 2.0
                    && longitudeDeltaDeg() <= 2.0
                    && groundDistanceKm() <= 100.0;
        }
    }

    public static double wrapLongitude(double deltaDeg) {
        double wrapped = Math.abs(deltaDeg) % 360.0;
        if (wrapped > 180.0) {
            wrapped = 360.0 - wrapped;
        }
        return wrapped;
    }

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }
}
