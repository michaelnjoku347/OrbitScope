import "cesium/Build/Cesium/Widgets/widgets.css";
import "./styles.css";
import { fetchCatalog, fetchCompact, fetchPasses, type Group, type SatelliteInfo } from "./api";
import { createGlobe } from "./globe";
import * as Cesium from "cesium";

const statusEl = document.querySelector("#status") as HTMLElement;
const infoEl = document.querySelector("#info") as HTMLElement;
const searchEl = document.querySelector("#search") as HTMLInputElement;
const filterButtons = [...document.querySelectorAll<HTMLButtonElement>("[data-filter]")];

let filter: Group = "ALL";
let query = "";
let names = new Map<number, SatelliteInfo>();

function formatTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    hour: "2-digit",
    minute: "2-digit",
    timeZoneName: "short",
  });
}

async function showInfo(id: number, lat: number, lon: number, alt: number) {
  const meta = names.get(id);
  infoEl.innerHTML = `<h2>${meta?.name ?? "Satellite " + id}</h2>
    <dl>
      <dt>NORAD</dt><dd>${id}</dd>
      <dt>Group</dt><dd>${meta?.group ?? ""}</dd>
      <dt>Lat / Lon</dt><dd>${lat.toFixed(2)}°, ${lon.toFixed(2)}°</dd>
      <dt>Altitude</dt><dd>${alt.toFixed(1)} km</dd>
    </dl>
    <p class="pass">Loading Freeport passes…</p>`;
  try {
    const result = await fetchPasses(id);
    const next = result.passes[0];
    const passHtml = next
      ? `<div class="pass"><strong>Next from ${result.observer.name}</strong><br>
         ${formatTime(next.start)} → ${formatTime(next.end)}<br>
         max elevation ${next.maxElevationDeg.toFixed(0)}°</div>`
      : `<div class="pass">No pass above 10° from ${result.observer.name} in the next 24 hours.</div>`;
    const extra = result.passes
      .slice(1, 4)
      .map((pass) => `<div class="pass">${formatTime(pass.peak)} · ${pass.maxElevationDeg.toFixed(0)}°</div>`)
      .join("");
    infoEl.insertAdjacentHTML("beforeend", passHtml + extra);
    const loading = infoEl.querySelector(".pass");
    if (loading && loading.textContent?.includes("Loading")) {
      loading.remove();
    }
  } catch (error) {
    infoEl.insertAdjacentHTML("beforeend", `<p class="pass">${(error as Error).message}</p>`);
  }
}

async function start() {
  const globe = await createGlobe("globe");
  const catalog = await fetchCatalog();
  names = new Map(catalog.satellites.map((row) => [row.id, row]));

  globe.viewer.screenSpaceEventHandler.setInputAction((click: { position: Cesium.Cartesian2 }) => {
    const id = globe.pickId(click.position);
    if (id === undefined) {
      return;
    }
    const frame = latest;
    if (!frame) {
      return;
    }
    const index = frame.ids.indexOf(id);
    showInfo(id, frame.latitude[index] ?? 0, frame.longitude[index] ?? 0, frame.altitudeKm[index] ?? 0);
  }, Cesium.ScreenSpaceEventType.LEFT_CLICK);

  filterButtons.forEach((button) => {
    button.addEventListener("click", () => {
      filter = button.dataset.filter as Group;
      filterButtons.forEach((other) => other.classList.toggle("active", other === button));
    });
  });
  searchEl.addEventListener("input", () => {
    query = searchEl.value;
  });

  let latest: Awaited<ReturnType<typeof fetchCompact>> | undefined;
  const tick = async () => {
    try {
      latest = await fetchCompact();
      const visible = globe.apply(latest, filter, query, names);
      statusEl.textContent = `${visible.toLocaleString()} dots · ${new Date(latest.time).toLocaleTimeString()}`;
    } catch (error) {
      statusEl.textContent = (error as Error).message;
    }
  };
  await tick();
  window.setInterval(tick, 1000);
}

start().catch((error: Error) => {
  statusEl.textContent = error.message;
});
