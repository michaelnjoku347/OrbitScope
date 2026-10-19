# OrbitScope

A Java project that will show every Starlink satellite moving around a 3D Earth in real time. This repo covers milestones 0–4: a first program, a Java warm-up, live TLE data, Orekit orbit math, and a Spring Boot JSON API.

## Run it

You need Java 21+ (`java -version`). Maven is included as `./mvnw`. Then:

```bash
./run.sh hello        # Milestone 0 — print a line
./run.sh warmup       # Milestone 1 — arrays, Satellite class, list methods
./run.sh file         # Milestone 2 — parse data/sample-starlink.tle
./run.sh fetch        # Milestone 2 — download live Starlink TLEs
./run.sh iss          # Milestone 3 — ISS position vs https://wheretheiss.at
./run.sh positions    # Milestone 3 — lat/lon/alt for ISS + a few Starlinks
./run.sh future       # Milestone 3 — same thing 90 minutes from now
./run.sh api          # Milestone 4 — JSON API at http://localhost:8080
./run.sh test
```

The first orbit-math run downloads [Orekit data](https://gitlab.orekit.org/orekit/orekit-data/-/archive/main/orekit-data-main.zip) into `orekit-data/` (leap seconds and Earth orientation). Maven is the build tool: `pom.xml` lists dependencies such as Orekit and Spring Boot, and `./mvnw` downloads them for you.

## API

Once `./run.sh api` is running:

- http://localhost:8080/ — index
- http://localhost:8080/satellites — every satellite in the catalog
- http://localhost:8080/satellites/25544/position — ISS right now
- http://localhost:8080/satellites/25544/position?at=2026-10-20T00:00:00Z — ISS at a future time
- http://localhost:8080/satellites/positions — current lat/lon/alt for the whole catalog

TLE data is fetched from CelesTrak on startup and refreshed every 3 hours.

## Milestones

**Milestone 0 — Setup.** Public repo, MIT license, a first program that prints a line.

**Milestone 1 — Java warm-up.** Store satellite names in an array and print them with a loop. Make a `Satellite` class with a name, altitude, and speed, then build a list of them. Write methods that find the highest satellite and the average altitude.

**Milestone 2 — Get real data.** Read a saved TLE text file, split it into groups of 3 lines, and turn each group into a `Satellite`. Then fetch the same data from the internet instead of a file.

**Milestone 3 — Orbit math.** Add Orekit with Maven. Compute where one satellite is right now as latitude, longitude, and altitude. Do it for all satellites, then for a future time. Done when the ISS matches a public tracker.

**Milestone 4 — Backend API.** Spring Boot endpoints for `/satellites` and `/satellites/{id}/position`, with automatic TLE refresh.

The long-term plan is a website with a 3D Earth, this Java backend for the orbit math, and a GPU-accelerated path that runs the same math much faster.

## Layout

```
pom.xml                 Maven build (dependencies + plugins)
src/main/java/orbitscope
data/                   sample TLE files
orekit-data/            downloaded on first orbit-math run (not in git)
```
