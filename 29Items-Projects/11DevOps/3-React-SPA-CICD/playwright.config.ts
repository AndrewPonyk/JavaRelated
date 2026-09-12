import { defineConfig, devices } from '@playwright/test';

const PORT = 4173;

/**
 * Two modes (docs/TECH-NOTES.md §3.2):
 *  - Hermetic (default): builds the app with MSW enabled (--mode e2e) and serves dist/ locally.
 *  - Live: set PLAYWRIGHT_BASE_URL to a deployed URL; deploy workflows run only @smoke specs.
 */
const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? `http://localhost:${PORT}`;

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 2 : undefined,
  reporter: [
    ['html', { open: 'never' }],
    ['junit', { outputFile: 'test-results/junit.xml' }],
    ...(process.env.CI ? ([['github']] as const) : []),
  ],
  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    // firefox + webkit run nightly (.github/workflows/nightly.yml), not on every PR.
    { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
    { name: 'webkit', use: { ...devices['Desktop Safari'] } },
  ],
  webServer: process.env.PLAYWRIGHT_BASE_URL
    ? undefined
    : {
        command: 'npm run e2e:serve',
        url: `http://localhost:${PORT}`,
        reuseExistingServer: !process.env.CI,
        timeout: 180_000, // includes the vite build
      },
});
