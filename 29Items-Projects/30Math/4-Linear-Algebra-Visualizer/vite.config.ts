/// <reference types="vitest/config" />
import { fileURLToPath, URL } from 'node:url';

import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// NOTE on local dev: plain `npm run dev` serves the SPA only (no /api — the app
// degrades to offline mode). Run `npm run dev:full` (vercel dev) for the full stack,
// or point VITE_API_BASE_URL at a deployed preview.
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    sourcemap: true,
    rollupOptions: {
      output: {
        // three.js dominates bundle size — isolate it so app-code changes
        // don't invalidate its long-lived CDN cache entry.
        manualChunks: { three: ['three'] },
      },
    },
  },
  test: {
    environment: 'jsdom', // api handler tests opt into node via @vitest-environment pragma
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}', 'tests/unit/**/*.test.ts'],
    coverage: {
      provider: 'v8',
      // Pure domain logic is held to a higher bar than UI glue — see docs/TECH-NOTES.md §3.2.
      include: ['src/core/**', 'src/services/**', 'src/state/**', 'api/**'],
      exclude: [
        'src/**/*.test.*',
        'src/**/index.ts',
        // Real PrismaClient construction — exercised only against a live DB.
        'api/_lib/db.ts',
      ],
      thresholds: {
        statements: 80,
        branches: 75,
        functions: 80,
        lines: 80,
      },
    },
  },
});
