# notes

oct 2026 — i'm building this as I go, not as a polished thing. dumping stuff here so i remember it for interviews / when i break it later.

## getting java to do anything

public repo, MIT, then I sat there with an empty folder. `HelloOrbitScope` printing a line felt stupid but at least git had a commit.

warmup week was arrays and a Satellite class. i still write the highest/average methods as regular for loops. streams make me lose the plot.

## TLE files are ugly on purpose

downloaded starlink.txt from celestrak by hand first. it's just:

```
NAME
1 ....
2 ....
```

over and over. once you see the pattern it's not scary.

then i fetched it instead of using the file. spent an evening getting back an empty response. they block default java user-agents. put the github url in the header and it started working. annoying.

## orekit

maven is just "list the library in pom.xml and it shows up." that part was fine.

orekit itself would not start. the error is about data providers / UTC-TAI. you have to download their orekit-data zip (leap seconds, earth orientation, a bunch of other stuff i don't use). i put that behind `OrekitSupport` so it grabs the zip if the folder isn't there.

once that's loaded: TLE in, lat/lon/alt out. i checked the ISS against wheretheiss.at. 0.2 km. i was expecting worse.

ITRF vs TEME vs whatever — i copied the frames from their tutorial and did not look back.

## the api

spring boot is a lot of annotations but `/satellites` and `/satellites/25544/position` actually did what i wanted on the first try. refresh every 3 hours because celestrak doesn't want you hammering them.

full position json for 10k sats was like 1.8MB. the globe was choking so i made `/satellites/positions/compact` with parallel float arrays. that one i'd actually show someone.

## cesium

i do not like javascript but typescript + vite was ok.

entities = lag. PointPrimitiveCollection = fine. click handling is a little janky, you have to actually hit the dot.

the page is just a dark overlay on cesium. search box, four filter buttons, that's it. i was not going to spend a week on css.

freeport is hard-coded as the observer because that's home. "when can i see it" is just: is the elevation > 10 degrees, sampled every 30 seconds. not fancy. ISS pops up a few times a day.

## "gpu"

i do not have an nvidia card here.

tore through tornadoVM docs. it's a java thing that compiles annotated loops to opencl/ptx. you don't rewrite in cuda, you write a tight loop over arrays and tag it `@Parallel`. that's why KeplerBatch exists — orekit objects can't go on a gpu.

so the benchmark is:
1. orekit sequential
2. orekit parallel streams (2.5x on 4 cores)
3. kepler arrays (100x, but it's a simpler model)

if i get on a machine with a gpu i'd plug tornadoVM into KeplerBatch. until then i'm not going to fake a gpu number.

## still todo

- actually deploy (render + vercel) and put it on njoku.dev
- gif of the globe rotating, readme has a screenshot for now
- tornadoVM for real
- click-to-select is easy to miss on starlink because the dots are tiny
