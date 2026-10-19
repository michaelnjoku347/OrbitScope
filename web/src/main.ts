import "cesium/Build/Cesium/Widgets/widgets.css";
import "./styles.css";
import { fetchCatalog, fetchCompact, fetchPasses, type CompactPositions, type Group, type SatelliteInfo } from "./api";
import { createGlobe } from "./globe";
import * as Cesium from "cesium";

// same idea as looking up nodes in javafx, just in the browser
const statusEl = document.getElementById("status") as HTMLElement;
const infoEl = document.getElementById("info") as HTMLElement;
const searchEl = document.getElementById("search") as HTMLInputElement;
const filterButtons = document.querySelectorAll("button[data-filter]");

let filter: Group = "ALL";
let query = "";
let names = new Map<number, SatelliteInfo>();
let latest: CompactPositions | undefined;

function formatTime(iso: string) {
  const date = new Date(iso);
  return date.toLocaleString(undefined, {
    hour: "2-digit",
    minute: "2-digit",
    timeZoneName: "short",
  });
}

async function showInfo(id: number, lat: number, lon: number, alt: number) {
  const meta = names.get(id);
  const title = meta ? meta.name : "Satellite " + id;
  const group = meta ? meta.group : "";

  infoEl.innerHTML =
    "<h2>" + title + "</h2>" +
    "<div>id: " + id + "</div>" +
    "<div>group: " + group + "</div>" +
    "<div>lat/lon: " + lat.toFixed(2) + ", " + lon.toFixed(2) + "</div>" +
    "<div>alt: " + alt.toFixed(1) + " km</div>" +
    "<p class=\"pass\">checking when it flies over home…</p>";

  try {
    const result = await fetchPasses(id);
    const next = result.passes[0];
    let html = "";
    if (next) {
      html +=
        "<div class=\"pass\"><b>next over " + result.observer.name + "</b><br>" +
        formatTime(next.start) + " → " + formatTime(next.end) + "<br>" +
        "max el " + next.maxElevationDeg.toFixed(0) + "°</div>";
    } else {
      html += "<div class=\"pass\">nothing above 10° from " + result.observer.name + " in the next day</div>";
    }
    for (let i = 1; i < result.passes.length && i < 4; i++) {
      const pass = result.passes[i];
      html += "<div class=\"pass\">" + formatTime(pass.peak) + " · " + pass.maxElevationDeg.toFixed(0) + "°</div>";
    }
    const loading = infoEl.querySelector(".pass");
    if (loading) {
      loading.remove();
    }
    infoEl.innerHTML += html;
  } catch (error) {
    infoEl.innerHTML += "<p class=\"pass\">" + (error as Error).message + "</p>";
  }
}

function handleFilterClick(button: Element) {
  filter = (button as HTMLButtonElement).dataset.filter as Group;
  filterButtons.forEach((other) => {
    if (other === button) {
      other.classList.add("active");
    } else {
      other.classList.remove("active");
    }
  });
}

async function tick(globe: Awaited<ReturnType<typeof createGlobe>>) {
  try {
    latest = await fetchCompact();
    const visible = globe.apply(latest, filter, query, names);
    statusEl.textContent = visible.toLocaleString() + " sats  " + new Date(latest.time).toLocaleTimeString();
  } catch (error) {
    statusEl.textContent = (error as Error).message;
  }
}

async function start() {
  const globe = await createGlobe("globe");
  const catalog = await fetchCatalog();

  names = new Map();
  for (let i = 0; i < catalog.satellites.length; i++) {
    const row = catalog.satellites[i];
    names.set(row.id, row);
  }

  // click handler — same job as a javafx onAction
  globe.viewer.screenSpaceEventHandler.setInputAction((click: { position: Cesium.Cartesian2 }) => {
    const id = globe.pickId(click.position);
    if (id === undefined || !latest) {
      return;
    }
    const index = latest.ids.indexOf(id);
    showInfo(id, latest.latitude[index] || 0, latest.longitude[index] || 0, latest.altitudeKm[index] || 0);
  }, Cesium.ScreenSpaceEventType.LEFT_CLICK);

  filterButtons.forEach((button) => {
    button.addEventListener("click", () => handleFilterClick(button));
  });
  searchEl.addEventListener("input", () => {
    query = searchEl.value;
  });

  await tick(globe);
  window.setInterval(() => {
    tick(globe);
  }, 1000);
}

start().catch((error: Error) => {
  statusEl.textContent = error.message;
});
