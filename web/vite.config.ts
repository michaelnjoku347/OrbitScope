import { defineConfig } from "vite";
import cesium from "vite-plugin-cesium";

export default defineConfig({
  plugins: [cesium()],
  server: {
    port: 5173,
    proxy: {
      "/satellites": "http://localhost:8080",
      "/api": "http://localhost:8080",
    },
  },
});
