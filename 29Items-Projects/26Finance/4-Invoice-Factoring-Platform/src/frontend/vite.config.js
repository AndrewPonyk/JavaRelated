var _a;
/// <reference types="node" />
import { defineConfig } from 'vitest/config'; // extends Vite's config with the `test` block
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
        // Proxy API calls to the .NET backend during local dev to avoid CORS.
        proxy: {
            '/api': {
                target: (_a = process.env.VITE_API_BASE_URL) !== null && _a !== void 0 ? _a : 'https://localhost:7080',
                changeOrigin: true,
                secure: false,
            },
        },
    },
    test: {
        globals: true,
        environment: 'jsdom',
    },
});
