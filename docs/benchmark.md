# timing notes

I ran this on a 4 core computer. no gpu.

I copied the sample satellites until I had 2048, then moved them 20 times (30 seconds apart). not a perfect test. I just wanted to see if using more cores helped.

| what | ms | vs 1 core orekit |
| --- | ---: | ---: |
| Orekit, 1 core | 405 | 1x |
| Orekit, 4 cores | 165 | 2.45x |
| my array version, 1 core | 9 | 45x |
| my array version, 4 cores | 4 | 101x |

the array one is less accurate. I skipped drag and a bunch of other stuff. the globe still uses Orekit.

there's a java gpu library called TornadoVM. I didn't run it. no gpu here.

run again: `./run.sh bench`
