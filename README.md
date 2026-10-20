# OrbitScope

Starlink, GPS, and the ISS on a 3D globe. Java/Orekit computes positions, Cesium draws them. Default observer is New York.

![globe](docs/globe.png)

## Run

Java 21 and Node.

```bash
./run.sh api    # http://localhost:8080
./run.sh web    # http://localhost:5173
```

Click a satellite for name, altitude, and the next pass over New York.

Orekit downloads `orekit-data/` on first run (not in git). CelesTrak sends an empty file if the User-Agent is missing.

```bash
./run.sh hello
./run.sh warmup
./run.sh file      # data/sample-starlink.tle
./run.sh fetch
./run.sh iss       # vs wheretheiss.at
./run.sh bench
./run.sh test
```

## Accuracy

ISS vs https://wheretheiss.at at the same timestamp was about 0.2 km.

## Timing

No GPU on this machine. Numbers are in [docs/benchmark.md](docs/benchmark.md).

- Orekit, 1 core: 405 ms
- Orekit, 4 cores: 165 ms
- array version: 4 ms (less accurate)

The globe uses Orekit.

## Layout

```
src/main/java/orbitscope
web/
data/
NOTES.md
```

`Dockerfile`, `render.yaml`, and `web/vercel.json` are in the repo. Not deployed yet.
