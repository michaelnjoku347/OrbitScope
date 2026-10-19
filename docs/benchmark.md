# OrbitScope benchmark

Workload: **2048** satellites × **20** time steps (30 s apart) on **4** CPU cores.
No NVIDIA GPU was attached, so the "GPU-shaped" path is a Kepler two-body kernel
over plain arrays, run with Java parallel streams (the same map a CUDA/TornadoVM
kernel would launch).

| Path | Time | Speedup vs Orekit sequential |
| --- | ---: | ---: |
| Orekit SGP4, 1 core | 405 ms | 1.00× |
| Orekit SGP4, all cores | 165 ms | 2.45× |
| Kepler arrays, 1 core | 9 ms | 45.00× |
| Kepler arrays, all cores | 4 ms | 101.25× |

The Kepler rewrite is the heavy-loop replacement. Hook it to TornadoVM or CUDA/JNI
on a machine with a GPU to go further; the kernel is already a per-satellite map
with no shared mutable state.
