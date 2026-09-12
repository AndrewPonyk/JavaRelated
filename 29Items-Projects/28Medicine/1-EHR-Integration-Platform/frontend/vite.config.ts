import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  server: {
    port: 5173,
    proxy: {
      // FHIR + patient admin → gateway (8081)
      '/fhir': { target: 'http://localhost:8081', changeOrigin: true },
      '/api/v1/patients': { target: 'http://localhost:8081', changeOrigin: true },
      // Risk stratification + entity linking → entity-linking service (8084)
      '/api/v1/stratification': { target: 'http://localhost:8084', changeOrigin: true },
      '/api/v1/link': { target: 'http://localhost:8084', changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
  },
});
