# timing

4 cores, no GPU. 2048 satellites × 20 steps (30s apart). Sample TLEs duplicated so the loop was long enough to measure.

| what | ms | vs Orekit 1 core |
| --- | ---: | ---: |
| Orekit, 1 core | 405 | 1x |
| Orekit, 4 cores | 165 | 2.45x |
| array version, 1 core | 9 | 45x |
| array version, 4 cores | 4 | 101x |

Array version skips drag. Globe uses Orekit.

`./run.sh bench`
