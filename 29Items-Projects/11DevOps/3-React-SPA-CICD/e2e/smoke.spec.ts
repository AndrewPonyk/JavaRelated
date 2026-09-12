import { expect, test } from '@playwright/test';

/**
 * @smoke specs run against LIVE environments after deploys (deploy-*.yml with
 * PLAYWRIGHT_BASE_URL set), so they must be read-only and idempotent:
 * no logins with shared accounts, no mutations. Docs: TECH-NOTES §3.2.
 */

test('portal is up and serves the app shell @smoke', async ({ page }) => {
  const consoleErrors: string[] = [];
  page.on('console', (message) => {
    if (message.type() !== 'error') return;
    // The anonymous boot legitimately receives 401s from the session probe
    // (/v1/auth/me → /v1/auth/refresh); the browser logs each failed resource as a
    // console error. Those are expected — anything else is a real defect.
    if (/failed to load resource.*401/i.test(message.text())) return;
    consoleErrors.push(message.text());
  });

  await page.goto('/');

  await expect(page).toHaveTitle(/customer portal/i);
  // Anonymous visitors are redirected to the login screen.
  await expect(page.getByRole('heading', { name: /sign in/i })).toBeVisible();

  expect(consoleErrors, `Console errors on load:\n${consoleErrors.join('\n')}`).toHaveLength(0);
});

test('deep links are handled by the SPA fallback, not a CDN 404 @smoke', async ({ page }) => {
  // Guards catchall_document / nginx try_files / CloudFront rewrite (TECH-NOTES §3.6 #4).
  const response = await page.goto('/settings');
  expect(response?.status()).toBeLessThan(400);
  await expect(page.getByRole('heading', { name: /sign in/i })).toBeVisible();
});
