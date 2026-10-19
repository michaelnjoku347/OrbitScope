# OrbitScope notes

What I learned while building this, milestone by milestone. Useful later as interview stories.

## Milestone 0 — a repo that exists

A public GitHub repo plus an MIT license is not "the project." The first real win was `HelloOrbitScope` printing one line, committed and pushed. After that, every other milestone had a place to land.

## Milestone 1 — objects before orbits

Arrays, `ArrayList`, a `Satellite` class, and two methods (`findHighest`, `averageAltitude`) are the whole Java warm-up. I kept those methods as explicit loops instead of streams so the first weeks stay readable.

## Milestone 2 — TLE is just text

A CelesTrak TLE file is groups of three lines: a name, then line 1, then line 2. `BufferedReader` + `split("\\R")` + a `for` loop that steps by 3 is enough to turn the file into objects. Fetching the same text over HTTP with `HttpClient` (and a real `User-Agent`) was the jump from "saved homework file" to "live catalog." CelesTrak will empty-response you if the User-Agent looks like a scraper.

## Milestone 3 — Orekit is a data context plus SGP4

Maven is the build tool: `pom.xml` lists Orekit, `./mvnw` downloads it. Orekit will not propagate a TLE until `orekit-data/` (leap seconds + Earth orientation) is on disk. After that, `TLE` → `TLEPropagator.selectExtrapolator` → ITRF → `GeodeticPoint` is latitude, longitude, altitude.

The done-check was the ISS vs https://wheretheiss.at. Same timestamp, two independent SGP4 implementations: **0.2 km** apart.

## Milestone 4 — JSON is the globe's food

Spring Boot `@RestController` + `@Scheduled` (every 3 hours) is the whole backend shape. `/satellites` lists, `/satellites/{id}/position` is one Orekit call. Opening that URL in a browser is the milestone.

## Milestone 5 — 10k dots need a tight payload

Cesium `PointPrimitiveCollection` can draw thousands of dots. The slow part is JSON. Full position objects were ~1.8 MB; the compact arrays (`ids`, `latitude`, `longitude`, `altitudeKm`, `group`) plus a 1-second cache on the Java side let the globe tick at 1 Hz. Vite proxies `/satellites` to `:8080` in dev so the browser can call a relative URL.

## Milestone 6 — filters are names, passes are elevation

Tagging `STARLINK` / `GPS` / `ISS` from the TLE name is enough for search chips. "When can I see it from Freeport?" is: pick a `TopocentricFrame` at 26.5333°N, 78.70°W, step Orekit 30 seconds at a time, and group the stretches where elevation ≥ 10°. ISS does that a few times a day.

## Milestone 7 — rewrite the heavy loop, then measure

The heavy loop is "every satellite, many time steps." Sequential Orekit SGP4 on 2048 sats × 20 steps: **405 ms**. The same work on 4 cores: **165 ms (2.45×)**. A Kepler two-body kernel over plain arrays (what a CUDA/TornadoVM thread would run): **4 ms (101× vs sequential SGP4)**.

There is no NVIDIA GPU on this machine, so TornadoVM/CUDA is the next hook, not a fake number. The kernel is already a per-satellite map with no shared mutable state.

## Milestone 8 — ship the story, not just the code

A recruiter needs a link, a GIF of the globe, an architecture picture, setup steps, and the speedup chart. Backend: Docker / Render. Frontend: Vercel with `VITE_API_URL`. README is that packet. This NOTES.md is the interview version.
