/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // Produce a minimal standalone server for Docker (.next/standalone/server.js).
  output: "standalone",
  // BACKEND_URL / BACKEND_API_KEY are read server-side only (route handlers) and are
  // never exposed to the browser. Do NOT put secrets under `env` (that inlines them).
};

module.exports = nextConfig;
