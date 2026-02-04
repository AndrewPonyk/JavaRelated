import { defineConfig, devices } from '@playwright/test';
import dotenv from 'dotenv';

// Load environment variables
dotenv.config({ path: '.env.local' });

/**
 * See https://playwright.dev/docs/test-configuration.
 */
export default defineConfig({
  testDir: './tests/e2e',

  /* Run tests in files in parallel */
  fullyParallel: true,

  /* Fail the build on CI if you accidentally left test.only in the source code. */
  forbidOnly: !!process.env.CI,

  /* Retry on CI only */
  retries: process.env.CI ? 2 : 0,

  /* Opt out of parallel tests on CI. */
  workers: process.env.CI ? 1 : undefined,

  /* Reporter to use. See https://playwright.dev/docs/test-reporters */
  reporter: [
    ['html', { outputFolder: 'test-results/html' }],
    ['json', { outputFile: 'test-results/results.json' }],
    ['junit', { outputFile: 'test-results/junit.xml' }],
    ['line'],
  ],

  /* Shared settings for all the projects below. See https://playwright.dev/docs/api/class-testoptions. */
  use: {
    /* Base URL to use in actions like `await page.goto('/')`. */
    baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:3000',

    /* Collect trace when retrying the failed test. See https://playwright.dev/docs/trace-viewer */
    trace: 'on-first-retry',

    /* Take screenshot on failure */
    screenshot: 'only-on-failure',

    /* Record video on failure */
    video: 'retain-on-failure',

    /* Global timeout for each action */
    actionTimeout: 10000,

    /* Global timeout for navigation */
    navigationTimeout: 30000,

    /* Ignore HTTPS errors */
    ignoreHTTPSErrors: true,

    /* Extra HTTP headers */
    extraHTTPHeaders: {
      'Accept': 'application/json',
    },
  },

  /* Configure projects for major browsers */
  projects: [
    {
      name: 'setup',
      testMatch: /.*\.setup\.ts/,
      teardown: 'cleanup',
    },

    {
      name: 'cleanup',
      testMatch: /.*\.teardown\.ts/,
    },

    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        // Custom viewport
        viewport: { width: 1280, height: 720 },
      },
      dependencies: ['setup'],
    },

    {
      name: 'firefox',
      use: {
        ...devices['Desktop Firefox'],
        viewport: { width: 1280, height: 720 },
      },
      dependencies: ['setup'],
    },

    {
      name: 'webkit',
      use: {
        ...devices['Desktop Safari'],
        viewport: { width: 1280, height: 720 },
      },
      dependencies: ['setup'],
    },

    /* Test against mobile viewports. */
    {
      name: 'Mobile Chrome',
      use: { ...devices['Pixel 5'] },
      dependencies: ['setup'],
    },

    {
      name: 'Mobile Safari',
      use: { ...devices['iPhone 12'] },
      dependencies: ['setup'],
    },

    /* Test against branded browsers. */
    {
      name: 'Microsoft Edge',
      use: {
        ...devices['Desktop Edge'],
        channel: 'msedge',
        viewport: { width: 1280, height: 720 },
      },
      dependencies: ['setup'],
    },

    {
      name: 'Google Chrome',
      use: {
        ...devices['Desktop Chrome'],
        channel: 'chrome',
        viewport: { width: 1280, height: 720 },
      },
      dependencies: ['setup'],
    },
  ],

  /* Test directory configuration */
  testIgnore: [
    '**/node_modules/**',
    '**/dist/**',
    '**/build/**',
  ],

  /* Expect timeout */
  expect: {
    timeout: 5000,
    toHaveScreenshot: {
      mode: 'strict',
      animations: 'disabled',
    },
    toMatchScreenshot: {
      mode: 'strict',
      animations: 'disabled',
    },
  },

  /* Global setup and teardown */
  globalSetup: require.resolve('./tests/e2e/support/global-setup'),
  globalTeardown: require.resolve('./tests/e2e/support/global-teardown'),

  /* Run your local dev server before starting the tests */
  webServer: process.env.CI ? undefined : [
    {
      command: 'npm run dev:backend',
      port: 3001,
      timeout: 120 * 1000,
      reuseExistingServer: !process.env.CI,
      env: {
        NODE_ENV: 'test',
        DATABASE_URL: process.env.TEST_DATABASE_URL || 'postgresql://postgres:postgres@localhost:5432/dashboard_test',
        REDIS_URL: process.env.TEST_REDIS_URL || 'redis://localhost:6379/1',
        JWT_SECRET: 'test-secret-key-for-testing-only',
      },
    },
    {
      command: 'npm run dev:frontend',
      port: 3000,
      timeout: 120 * 1000,
      reuseExistingServer: !process.env.CI,
      env: {
        VITE_API_URL: 'http://localhost:3001',
        VITE_WS_URL: 'ws://localhost:3002',
      },
    },
  ],

  /* Output directory for test results */
  outputDir: './test-results',

  /* Maximum time for entire test run */
  globalTimeout: process.env.CI ? 30 * 60 * 1000 : undefined, // 30 minutes on CI

  /* Maximum time per test */
  timeout: 30 * 1000, // 30 seconds

  /* Metadata */
  metadata: {
    'Test Environment': process.env.NODE_ENV || 'test',
    'Base URL': process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:3000',
    'API URL': process.env.VITE_API_URL || 'http://localhost:3001',
  },
});