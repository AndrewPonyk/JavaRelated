import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    // dev: proxy API calls to the local backend container
    proxy: {
      '/api': { target: 'http://localhost:8000', changeOrigin: true },
    },
  },
  build: {
    outDir: 'dist',
    // security-scanner frontend should be lean; chunk-split vendor lib
    rollupOptions: {
      output: {
        manualChunks: { vendor: ['vue', 'vue-router'] },
      },
    },
  },
  test: {
    environment: 'happy-dom',
    include: ['tests/**/*.test.js'],
    // one process per file: no DOM timers leak across test files
    pool: 'forks',
  },
})
