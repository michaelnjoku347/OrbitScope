# OrbitScope

I wanted to see Starlink from home (Freeport, Bahamas) without staring at a table of numbers. This is that: a Java backend that does the orbit math, and a Cesium page that draws a dot for every satellite.

It's a learning project. Some of the early files (`Warmup`, `HelloOrbitScope`) are me figuring out Java. I left them in.

![globe with a bunch of starlink dots](docs/globe.png)

## Running it

Java 21 and Node. Maven wrapper is already in the repo.

```bash
./run.sh api    # backend, http://localhost:8080
./run.sh web    # globe,   http://localhost:5173
```

Open the second one. If you get "Failed to fetch" I forgot to start the API again.

Click a dot for name / altitude. Search box and the Starlink / GPS / ISS buttons filter the cloud. Pass times are from Freeport unless you pass `?lat=&lon=` to the API.

Other stuff I used while building:

```bash
./run.sh hello      # first program
./run.sh warmup     # arrays / Satellite class
./run.sh file       # parse data/sample-starlink.tle
./run.sh fetch      # live names from CelesTrak
./run.sh iss        # my position vs wheretheiss.at
./run.sh bench      # timing
./run.sh test
```

First time you touch orbit math it downloads Orekit's data files into `orekit-data/` (not in git, they're big). If that folder is missing, Orekit just dies with a confusing error.

## Is the math any good?

I pointed Orekit at the ISS and compared it to https://wheretheiss.at at the same timestamp. About **0.2 km** apart. Close enough that I'm not going to argue with it.

TLE data comes from CelesTrak. They want a real User-Agent or they hand you an empty file. I found that out the slow way.

## Speed

I timed "propagate a few thousand satellites across 20 time steps" three ways. No GPU on this machine, so this is CPU only.

- Orekit SGP4 on one core: 405 ms
- same thing on 4 cores: 165 ms (~2.5×)
- a simplified Kepler version I wrote as arrays: 4 ms (~100× vs sequential SGP4)

The Kepler one is dumber (ignores drag, etc). It's what I'd try to throw at TornadoVM or CUDA if I had a card. Writeup is in [docs/benchmark.md](docs/benchmark.md).

## Layout

```
src/main/java/orbitscope   Java — parsing, Orekit, API
web/                       Cesium / TypeScript
data/                      sample TLE files I saved
NOTES.md                   stuff I don't want to forget
```

## Putting it online

Haven't actually deployed this yet. Plan:

- API: Docker on Render (`render.yaml`) or Fly
- frontend: Vercel, set `VITE_API_URL` to the API
- then link it from njoku.dev

`docker build -t orbitscope .` then `docker run -p 8080:8080 orbitscope` if you want both in one container.
