import { defineConfig } from "vitest/config";

// Vitest config kept separate from vite.config.ts to avoid a Vite/Vitest
// nested-dependency type clash. Vitest auto-loads this over vite.config.ts.
export default defineConfig({
  test: {
    globals: true,
    environment: "jsdom",
  },
});
