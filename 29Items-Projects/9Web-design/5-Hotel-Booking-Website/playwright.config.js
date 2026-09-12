module.exports = {
  testDir: "tests/e2e",
  use: {
    baseURL: "http://127.0.0.1:8888",
  },
  webServer: {
    command: "npm run dev",
    url: "http://127.0.0.1:8888",
    reuseExistingServer: !process.env.CI,
    timeout: 120000,
  },
};
