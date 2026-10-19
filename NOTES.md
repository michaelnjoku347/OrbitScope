# notes

CelesTrak returns an empty body unless User-Agent is set.

Orekit needs `orekit-data` (leap seconds, etc) or it fails on UTC-TAI. `OrekitSupport` downloads the zip if the folder is missing.

ISS vs wheretheiss.at: ~0.2 km.

`/satellites/positions` was ~1.8MB. `/satellites/positions/compact` is parallel arrays of lat/lon/alt.

TLE refresh is 3 hours.

Cesium `Entity` collection lagged at ~10k points. Globe uses `PointPrimitiveCollection`. Dots are easy to miss when clicking.

Passes: elevation >= 10°, sampled every 30s. Observer is Freeport.

`KeplerBatch` is only for the benchmark. No GPU here, so no TornadoVM numbers.
