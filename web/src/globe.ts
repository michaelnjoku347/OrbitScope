import * as Cesium from "cesium";
import type { CompactPositions, Group } from "./api";

const COLORS: Record<string, Cesium.Color> = {
  STARLINK: Cesium.Color.fromCssColorString("#3ee0c3"),
  GPS: Cesium.Color.fromCssColorString("#8be38b"),
  ISS: Cesium.Color.fromCssColorString("#f5c16c"),
  OTHER: Cesium.Color.fromCssColorString("#7aa2f7"),
};

export interface GlobeHandle {
  viewer: Cesium.Viewer;
  apply: (
    frame: CompactPositions,
    filter: Group,
    query: string,
    names: Map<number, { name: string }>,
  ) => number;
  pickId: (windowPosition: Cesium.Cartesian2) => number | undefined;
}

export async function createGlobe(container: string): Promise<GlobeHandle> {
  const imagery = await Cesium.TileMapServiceImageryProvider.fromUrl(
    Cesium.buildModuleUrl("Assets/Textures/NaturalEarthII"),
  );

  const viewer = new Cesium.Viewer(container, {
    baseLayer: new Cesium.ImageryLayer(imagery),
    terrainProvider: new Cesium.EllipsoidTerrainProvider(),
    animation: false,
    timeline: false,
    geocoder: false,
    homeButton: false,
    sceneModePicker: true,
    baseLayerPicker: false,
    navigationHelpButton: false,
    fullscreenButton: false,
    infoBox: false,
    selectionIndicator: false,
  });
  viewer.scene.globe.enableLighting = true;
  viewer.scene.backgroundColor = Cesium.Color.fromCssColorString("#070b14");
  viewer.scene.moon = undefined;
  viewer.clock.shouldAnimate = false;

  viewer.entities.add({
    id: "freeport",
    name: "Freeport, Bahamas",
    position: Cesium.Cartesian3.fromDegrees(-78.7, 26.5333),
    point: {
      pixelSize: 11,
      color: Cesium.Color.fromCssColorString("#ff7a59"),
      outlineColor: Cesium.Color.WHITE,
      outlineWidth: 1,
    },
    label: {
      text: "Freeport",
      font: "12px IBM Plex Sans, sans-serif",
      fillColor: Cesium.Color.WHITE,
      pixelOffset: new Cesium.Cartesian2(18, -14),
      disableDepthTestDistance: Number.POSITIVE_INFINITY,
      showBackground: true,
      backgroundColor: Cesium.Color.fromCssColorString("#10192bcc"),
    },
  });

  const points = viewer.scene.primitives.add(new Cesium.PointPrimitiveCollection());

  const apply = (
    frame: CompactPositions,
    filter: Group,
    query: string,
    names: Map<number, { name: string }>,
  ): number => {
    const needle = query.trim().toUpperCase();
    points.removeAll();
    let visible = 0;
    for (let i = 0; i < frame.count; i++) {
      const group = frame.group[i] ?? "OTHER";
      if (filter !== "ALL" && group !== filter) {
        continue;
      }
      const name = names.get(frame.ids[i])?.name.toUpperCase() ?? "";
      if (
        needle &&
        !String(frame.ids[i]).includes(needle) &&
        !group.includes(needle) &&
        !name.includes(needle)
      ) {
        continue;
      }
      const lat = frame.latitude[i];
      const lon = frame.longitude[i];
      if (!Number.isFinite(lat) || !Number.isFinite(lon)) {
        continue;
      }
      const pixelSize = group === "ISS" ? 10 : group === "GPS" ? 5 : 3;
      points.add({
        id: frame.ids[i],
        position: Cesium.Cartesian3.fromDegrees(lon, lat, Math.max(frame.altitudeKm[i], 0) * 1000),
        color: COLORS[group] ?? COLORS.OTHER,
        pixelSize,
        outlineWidth: group === "ISS" ? 1 : 0,
        outlineColor: Cesium.Color.WHITE,
      });
      visible += 1;
    }
    return visible;
  };

  const pickId = (windowPosition: Cesium.Cartesian2): number | undefined => {
    const picked = viewer.scene.pick(windowPosition);
    if (!Cesium.defined(picked)) {
      return undefined;
    }
    const value = picked.id;
    if (typeof value === "number") {
      return value;
    }
    if (value && typeof value.id === "string" && value.id !== "freeport") {
      return Number(value.id);
    }
    return undefined;
  };

  viewer.camera.setView({
    destination: Cesium.Cartesian3.fromDegrees(-78.7, 18.0, 18_000_000),
  });

  return { viewer, apply, pickId };
}
