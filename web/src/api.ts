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

async function get<T>(path: string): Promise<T> {
  const response = await fetch(`${API}${path}`);
  if (!response.ok) {
    throw new Error(`${path} failed: ${response.status}`);
  }
  return response.json() as Promise<T>;
}

export function fetchCatalog() {
  return get<{ count: number; satellites: SatelliteInfo[] }>("/satellites");
}

export function fetchCompact() {
  return get<CompactPositions>("/satellites/positions/compact");
}

export function fetchPasses(id: number) {
  return get<{ observer: { name: string }; passes: Pass[] }>(`/satellites/${id}/passes`);
}
