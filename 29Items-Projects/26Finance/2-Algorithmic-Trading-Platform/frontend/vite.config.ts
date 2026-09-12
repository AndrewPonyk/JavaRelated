import react from '@vitejs/plugin-react';
// vitest/config re-exports Vite's defineConfig augmented with the `test` field.
import { defineConfig } from 'vitest/config';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // Proxy API calls to the FastAPI gateway during dev (avoids CORS).
      '/api': 'http://localhost:8000',
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
  },
});
