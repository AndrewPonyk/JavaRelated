import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
  },
  build: {
    chunkSizeWarningLimit: 700,
    rollupOptions: {
      output: {
        manualChunks: {
          maps: ["leaflet", "react-leaflet", "mapbox-gl"],
          deck: ["@deck.gl/react", "@deck.gl/layers"],
        },
      },
    },
  },
  test: {
    environment: "jsdom",
  },
});
