import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// https://vitejs.dev/config/
export default defineConfig(({ command, mode }) => {
  // Load env file based on `mode` in the current working directory.
  const env = loadEnv(mode, process.cwd(), '');

  return {
    plugins: [
      react({
        // Enable React Fast Refresh
        fastRefresh: true,
        // Include JSX runtime for React 18
        jsxRuntime: 'automatic',
      }),
    ],

    // Path resolution
    resolve: {
      alias: {
        '@': resolve(__dirname, '../src'),
        '@components': resolve(__dirname, '../src/components'),
        '@pages': resolve(__dirname, '../src/pages'),
        '@hooks': resolve(__dirname, '../src/hooks'),
        '@services': resolve(__dirname, '../src/services'),
        '@utils': resolve(__dirname, '../src/utils'),
        '@types': resolve(__dirname, '../src/types'),
        '@stores': resolve(__dirname, '../src/stores'),
        '@shared': resolve(__dirname, '../shared'),
        '@assets': resolve(__dirname, '../src/assets'),
      },
    },

    // Development server configuration
    server: {
      port: 3000,
      host: '0.0.0.0', // Allow external connections
      open: false, // Don't try to open browser in Docker
      cors: true,

      // Proxy API requests to backend
      // Use process.env for Docker environment, fallback to env file, then localhost
      proxy: {
        '/api': {
          target: process.env.VITE_API_URL || env.VITE_API_URL || 'http://localhost:3001',
          changeOrigin: true,
          secure: false,
          configure: (proxy, _options) => {
            proxy.on('error', (err, _req, _res) => {
              console.log('proxy error', err);
            });
            proxy.on('proxyReq', (proxyReq, req, _res) => {
              console.log('Sending Request to the Target:', req.method, req.url);
            });
            proxy.on('proxyRes', (proxyRes, req, _res) => {
              console.log('Received Response from the Target:', proxyRes.statusCode, req.url);
            });
          },
        },
        // WebSocket proxy for real-time updates
        '/ws': {
          target: process.env.VITE_WS_URL || env.VITE_WS_URL || 'ws://localhost:3002',
          ws: true,
          changeOrigin: true,
        },
      },

      // HMR settings
      hmr: {
        overlay: true,
      },
    },

    // Preview server (for production builds)
    preview: {
      port: 3000,
      host: '0.0.0.0',
    },

    // Build configuration
    build: {
      outDir: 'dist',
      emptyOutDir: true,
      sourcemap: command === 'serve', // Source maps in development only

      // Bundle splitting for better caching
      rollupOptions: {
        output: {
          manualChunks: {
            // Vendor chunks
            'react-vendor': ['react', 'react-dom'],
            'react-router': ['react-router-dom'],
            'tanstack-query': ['@tanstack/react-query'],

            // Chart libraries
            'd3-vendor': ['d3', 'd3-selection', 'd3-scale', 'd3-axis'],
          },
        },
      },

      // Bundle size optimization
      chunkSizeWarningLimit: 1000,

      // Asset optimization
      assetsInlineLimit: 4096, // 4kb

      // CSS code splitting
      cssCodeSplit: true,

      // Minification (using esbuild - built into Vite)
      minify: 'esbuild',
    },

    // CSS configuration
    css: {
      modules: {
        // CSS modules naming pattern
        generateScopedName: '[name]__[local]__[hash:base64:5]',
      },

      devSourcemap: true,
    },

    // Environment variables
    envPrefix: 'VITE_',

    define: {
      // Global constants
      __APP_VERSION__: JSON.stringify(process.env.npm_package_version),
      __BUILD_TIME__: JSON.stringify(new Date().toISOString()),
    },

    // Optimization
    optimizeDeps: {
      include: [
        'react',
        'react-dom',
        'react-router-dom',
        '@tanstack/react-query',
        'd3',
        'date-fns',
        'lodash-es',
      ],
      exclude: [
        // Exclude packages that should not be pre-bundled
      ],
    },

    // Worker configuration for Web Workers
    worker: {
      format: 'es',
      plugins: () => [react()],
    },

    // JSON configuration
    json: {
      namedExports: true,
      stringify: false,
    },

    // ESBuild configuration
    esbuild: {
      // Drop console and debugger in production
      drop: command === 'build' ? ['console', 'debugger'] : [],
    },

    // Test configuration (for Vitest)
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      css: false,
    },

    // Experimental features
    experimental: {
      renderBuiltUrl(filename, { hostType }) {
        if (hostType === 'js') {
          return { js: `/${filename}` };
        } else {
          return { relative: true };
        }
      },
    },

    // Legacy browser support (if needed)
    legacy: {
      // Set to true if you need to support legacy browsers
      targets: ['defaults', 'not IE 11'],
    },

    // Performance monitoring
    logLevel: 'info',
    clearScreen: false,

  };
});