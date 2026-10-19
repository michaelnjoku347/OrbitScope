declare module "vite-plugin-cesium" {
  import type { Plugin } from "vite";
  export default function cesium(options?: Record<string, unknown>): Plugin;
}
