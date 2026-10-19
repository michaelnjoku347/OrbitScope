# notes

before this the last thing I built myself was a calculator in javafx. this is way bigger. writing things down so I remember them when I have to explain the project.

## java

HelloOrbitScope is just println. felt dumb but I needed a first commit.

warmup is arrays + a Satellite class. findHighest / averageAltitude are regular for loops. I still think of it like looping over a list of buttons in javafx. streams confuse me.

## tle files

celestrak files look like:

```
NAME
1 ....
2 ....
```

over and over. parser just skips blank lines and takes groups of 3.

downloading was worse. they block java's default user-agent so I got an empty file for a long time. put my github url in the header and it worked.

## orekit (I don't really get this)

I cannot do orbital mechanics. orekit is a library that does it.

it won't even start without a zip of leap seconds / earth data. OrekitSupport downloads that if the folder is missing.

after that: give it the 2 TLE lines, get lat/lon/alt back. I compared ISS to wheretheiss.at. 0.2 km. I was expecting to be way off.

the frame names (ITRF etc) I copied from their tutorial. I did not change them.

## spring boot

this is the "controllers" part, like javafx but the UI is a website.

`@GetMapping("/satellites")` means that url returns json. I followed a tutorial.

refresh every 3 hours because celestrak doesn't want you downloading constantly.

the full json was like 1.8MB and the globe got slow, so I made a second url that is just arrays of numbers. `/satellites/positions/compact`

## the globe

I had never made a real javascript app. vite runs the page. cesium draws the earth.

drawing 10k cesium "entities" lagged so I switched to PointPrimitiveCollection like a stackoverflow post said.

clicking is annoying. the dots are tiny. you have to actually hit one.

css is a dark box on top of the globe. I was not going to spend a week making it pretty. javafx css was the same vibe.

home = freeport. "when can I see it" = is it more than 10 degrees above the horizon, check every 30 seconds. that's it. I didn't get orekit's fancy detector working.

## gpu / tornadovm

I don't have an nvidia gpu.

people said tornadovm can run java loops on a gpu. I don't have one so I didn't actually do that. KeplerBatch is just the same math using arrays because that's what you'd send to a gpu.

benchmark is:

1. orekit one core
2. orekit more cores (parallelStream)
3. my array version

if I ever get a gpu I would try tornadovm on KeplerBatch. not faking a number.

## still todo

- put it on the internet (render / vercel) and link njoku.dev
- gif of the globe. screenshot in the readme for now
- actually try tornadovm
- clicking starlink is hard because the dots are small
