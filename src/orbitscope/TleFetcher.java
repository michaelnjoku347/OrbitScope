package orbitscope;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Milestone 2: fetch live Starlink TLE data from CelesTrak over HTTP
 * instead of reading a file you downloaded by hand.
 */
public class TleFetcher {
    public static final String STARLINK_TLE_URL =
            "https://celestrak.org/NORAD/elements/gp.php?GROUP=starlink&FORMAT=tle";

    private static final String USER_AGENT =
            "OrbitScope/0.1 (https://github.com/michaelnjoku347/OrbitScope)";

    public String fetchStarlinkTles() throws IOException, InterruptedException {
        return fetch(STARLINK_TLE_URL);
    }

    public String fetch(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/plain")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("CelesTrak request failed: HTTP " + response.statusCode()
                    + " from " + url);
        }

        String body = response.body();
        if (body == null || body.isBlank()) {
            throw new IOException("CelesTrak returned an empty TLE catalog from " + url);
        }
        return body;
    }
}
