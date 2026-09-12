import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import topLevelAwait from 'vite-plugin-top-level-await';
import wasm from 'vite-plugin-wasm';

// Cornerstone3D pulls in WASM codecs/segmentation, which need the wasm +
// top-level-await plugins. Its codecs also want cross-origin isolation
// (SharedArrayBuffer) for the dev server.
export default defineConfig({
  plugins: [react(), wasm(), topLevelAwait()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      // Segmentation WASM is unused by this viewer and can't be bundled cleanly.
      '@icr/polyseg-wasm': fileURLToPath(
        new URL('./src/stubs/polyseg-wasm.ts', import.meta.url),
      ),
    },
  },
  optimizeDeps: {
    exclude: ['@cornerstonejs/dicom-image-loader'],
  },
  worker: {
    format: 'es',
    plugins: () => [wasm(), topLevelAwait()],
  },
  server: {
    port: 5173,
    headers: {
      'Cross-Origin-Opener-Policy': 'same-origin',
      'Cross-Origin-Embedder-Policy': 'require-corp',
    },
    proxy: {
      '/api': { target: 'http://localhost:8000', changeOrigin: true },
    },
  },
});
