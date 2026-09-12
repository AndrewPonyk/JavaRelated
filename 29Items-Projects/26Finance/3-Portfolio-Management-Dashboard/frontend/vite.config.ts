import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // Proxy API calls to the backend during local dev.
      "/api": { target: "http://localhost:8000", changeOrigin: true },
    },
  },
});
