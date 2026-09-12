import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: process.env.CI ? [['html', { open: 'never' }], ['github']] : 'list',
  use: {
    baseURL: 'http://localhost:4173',
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        // Headless CI runners have no GPU — SwiftShader keeps WebGL available.
        // See docs/TECH-NOTES.md §3.6 pitfall 5.
        launchOptions: { args: ['--use-angle=swiftshader'] },
      },
    },
  ],
  webServer: {
    // `vite preview` serves the last production build — run `npm run build` first
    // (CI does; locally the e2e script assumes a fresh build exists).
    command: 'npm run preview -- --port 4173 --strictPort',
    url: 'http://localhost:4173',
    reuseExistingServer: !process.env.CI,
    timeout: 30_000,
  },
});
