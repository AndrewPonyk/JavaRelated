import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Local dev talks to the FastAPI dev server through this proxy, so the
    // app code can use relative /api URLs and VITE_API_BASE_URL stays empty.
    proxy: {
      "/api": "http://localhost:8000",
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: "./src/setupTests.ts",
    css: false,
    // Testing Library's auto-cleanup registers on the global afterEach.
    globals: true,
  },
});
