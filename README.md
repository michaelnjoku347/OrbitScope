# OrbitScope

A Java project that will show every Starlink satellite moving around a 3D Earth in real time. Right now it covers the first three milestones: a first program, a Java warm-up, and live TLE data from CelesTrak.

## Run it

You need Java 21+ (`java -version`). Then:

```bash
./run.sh hello     # Milestone 0 — print a line
./run.sh warmup    # Milestone 1 — arrays, Satellite class, list methods
./run.sh file      # Milestone 2 — parse data/sample-starlink.tle
./run.sh fetch     # Milestone 2 — download live Starlink TLEs
./run.sh test      # built-in checks
```

`./run.sh fetch` talks to [CelesTrak](https://celestrak.org/NORAD/elements/gp.php?GROUP=starlink&FORMAT=tle) and prints every satellite name, then the count.

## Milestones

**Milestone 0 — Setup.** Public repo, MIT license, a first program that prints a line.

**Milestone 1 — Java warm-up.** Store satellite names in an array and print them with a loop. Make a `Satellite` class with a name, altitude, and speed, then build a list of them. Write methods that find the highest satellite and the average altitude.

**Milestone 2 — Get real data.** Read a saved TLE text file, split it into groups of 3 lines, and turn each group into a `Satellite`. Then fetch the same data from the internet instead of a file. Done when the program prints the names and count of live Starlink satellites.

The long-term plan is a website with a 3D Earth, a Java backend for the orbit math, and a GPU-accelerated path that runs the same math much faster.

## Layout

```
src/orbitscope/     Java source
data/               sample TLE file (download a full set from CelesTrak if you want)
run.sh              compile and run
```
