export type Group = "STARLINK" | "GPS" | "ISS" | "OTHER" | "ALL";

export interface SatelliteInfo {
  id: number;
  name: string;
  group: string;
}

export interface CompactPositions {
  time: string;
  count: number;
  ids: number[];
  latitude: number[];
  longitude: number[];
  altitudeKm: number[];
  group: string[];
}

export interface Pass {
  start: string;
  peak: string;
  end: string;
  maxElevationDeg: number;
}

const API = import.meta.env.VITE_API_URL ?? "";

async function getJson(path: string) {
  const response = await fetch(API + path);
  if (!response.ok) {
    throw new Error("couldn't load " + path + " (" + response.status + ")");
  }
  return response.json();
}

export function fetchCatalog(): Promise<{ satellites: SatelliteInfo[] }> {
  return getJson("/satellites");
}

export function fetchCompact(): Promise<CompactPositions> {
  return getJson("/satellites/positions/compact");
}

export function fetchPasses(id: number): Promise<{ observer: { name: string }; passes: Pass[] }> {
  return getJson("/satellites/" + id + "/passes");
}
