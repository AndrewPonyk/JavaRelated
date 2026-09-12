import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// Dev proxy keeps the SPA same-origin with the API (no CORS locally).
// In AWS, CloudFront routes /api/* to the ALB instead.
// RTAP_PROXY_TARGET is deliberately NOT VITE_-prefixed: it is a server-side proxy
// target (e.g. http://analytics-api:8080 inside compose) and must never leak into
// browser code — the browser always talks same-origin through the proxy.
export default defineConfig({
  plugins: [react()],
  server: {
    host: true, // reachable from outside the container in compose
    proxy: {
      '/api': {
        target: process.env.RTAP_PROXY_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['src/test/setup.ts'],
    clearMocks: true, // call counts never leak between tests
  },
});
