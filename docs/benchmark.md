# timing notes

I ran this on a 4-core box with no GPU.

Work: 2048 satellites × 20 steps, 30 seconds apart. (I duplicated the sample TLEs so the loop was big enough to measure. Not scientifically perfect.)

| what | ms | vs orekit 1-core |
| --- | ---: | ---: |
| Orekit SGP4, 1 core | 405 | 1× |
| Orekit SGP4, 4 cores | 165 | 2.45× |
| my Kepler thing, 1 core | 9 | 45× |
| Kepler, 4 cores | 4 | 101× |

Kepler is cheating a bit — no drag, circular-ish, I convert ECI with a GMST hack. It's the version I'd try on a GPU (TornadoVM / CUDA) because it's just arrays.

Orekit is the one the globe actually uses.

Re-run: `./run.sh bench`
