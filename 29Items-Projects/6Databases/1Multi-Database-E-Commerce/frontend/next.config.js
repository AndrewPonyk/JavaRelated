/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // Produce a minimal standalone server for slim production images.
  output: 'standalone',
  // Surface the API base URL to the browser bundle.
  env: {
    NEXT_PUBLIC_API_BASE_URL:
      process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080/api/v1',
  },
};

module.exports = nextConfig;
