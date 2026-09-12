import { fileURLToPath, URL } from 'node:url';

import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    strictPort: true,
    // When a live dev API exists, proxy it here (and set VITE_ENABLE_MSW=false):
    // proxy: { '/api': { target: 'https://dev-api.example.com', changeOrigin: true } },
  },
  preview: {
    port: 4173,
    strictPort: true,
  },
  build: {
    outDir: 'dist',
    target: 'es2022',
    // Sourcemaps ship to the CDN today (private previews); switch to `hidden` + error-reporter
    // upload once Sentry is wired (Phase 2).
    sourcemap: true,
    rollupOptions: {
      output: {
        // Keep the framework in its own long-lived chunk so app changes don't bust it.
        manualChunks: {
          react: ['react', 'react-dom', 'react-router-dom'],
        },
      },
    },
  },
});
