import { test as base, type Page } from '@playwright/test';

/** Matches the seeded mock user (mocks/db/store.ts DEMO_USER). */
export const DEMO_USER = {
  email: 'demo@example.com',
  password: 'Password123!',
} as const;

interface AuthFixtures {
  /** A page already signed in via the real UI login flow, landed on the dashboard. */
  loggedInPage: Page;
}

/**
 * UI login on purpose: the access token lives in memory (no storageState shortcut exists),
 * and the login journey is cheap against the hermetic MSW build. If login ever becomes
 * the E2E bottleneck, add an API-based session seam here rather than in each spec.
 */
export const test = base.extend<AuthFixtures>({
  loggedInPage: async ({ page }, use) => {
    await page.goto('/login');
    await page.getByLabel(/e-mail/i).fill(DEMO_USER.email);
    await page.getByLabel(/password/i).fill(DEMO_USER.password);
    await page.getByRole('button', { name: /sign in/i }).click();
    await page.getByRole('heading', { name: /welcome back/i }).waitFor();
    await use(page);
  },
});

export { expect } from '@playwright/test';
