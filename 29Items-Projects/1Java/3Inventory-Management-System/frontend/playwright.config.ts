import { defineConfig, devices } from '@playwright/test'

const executablePath = process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH
const externalBaseUrl = process.env.PLAYWRIGHT_BASE_URL
const baseURL = externalBaseUrl ?? 'http://127.0.0.1:8081'

export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  webServer: externalBaseUrl
    ? undefined
    : {
        command: 'npm run dev -- --host 127.0.0.1 --port 8081',
        url: baseURL,
        reuseExistingServer: true,
        timeout: 60_000,
      },
  use: {
    baseURL,
    launchOptions: executablePath ? { executablePath } : undefined,
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
