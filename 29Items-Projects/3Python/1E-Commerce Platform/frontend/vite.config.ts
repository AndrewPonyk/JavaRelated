import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

// |su:63 Vite: modern build tool, faster than webpack (native ES modules, no bundling in dev)
export default defineConfig({
  plugins: [react()],
  resolve: {
    // |su:64 Path aliases: import from '@components/Button' instead of '../../../components/Button'
    // Must also be configured in tsconfig.json for TypeScript to understand
    alias: {
      '@': path.resolve(__dirname, './src'),
      '@components': path.resolve(__dirname, './src/components'),
      '@pages': path.resolve(__dirname, './src/pages'),
      '@hooks': path.resolve(__dirname, './src/hooks'),
      '@services': path.resolve(__dirname, './src/services'),
      '@types': path.resolve(__dirname, './src/types'),
      '@utils': path.resolve(__dirname, './src/utils'),
    },
  },
  server: {
    port: 3000,
    // |su:65 Proxy: forwards /api/* requests to backend during development
    // Avoids CORS issues (browser sees same origin: localhost:3000)
    // In production, nginx/load balancer handles this routing
    proxy: {
      '/api': {
        target: 'http://localhost:8000',
        changeOrigin: true,  // Changes Origin header to target URL
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,  // Generate .map files for debugging production errors
    rollupOptions: {
      output: {
        // |su:66 Code splitting: separate bundles for vendor libs (rarely change)
        // Browser caches vendor.js separately from app code
        // When you deploy new app code, users don't re-download React
        manualChunks: {
          vendor: ['react', 'react-dom', 'react-router-dom'],
          query: ['@tanstack/react-query'],
        },
      },
    },
  },
});
