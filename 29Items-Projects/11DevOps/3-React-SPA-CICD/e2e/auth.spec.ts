import { DEMO_USER, expect, test } from './fixtures/auth.fixture';

// Hermetic-only journeys (they log in / mutate) — excluded from live @smoke runs.

test.describe('authentication', () => {
  test('rejects wrong credentials with a visible error and stays on /login', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel(/e-mail/i).fill(DEMO_USER.email);
    await page.getByLabel(/password/i).fill('wrong-password-1');
    await page.getByRole('button', { name: /sign in/i }).click();

    await expect(page.getByRole('alert')).toContainText(/incorrect/i);
    await expect(page).toHaveURL(/\/login$/);
  });

  test('valid login lands on the dashboard', async ({ loggedInPage: page }) => {
    await expect(page.getByRole('heading', { name: /welcome back/i })).toBeVisible();
    await expect(page.getByRole('navigation', { name: /primary/i })).toBeVisible();
  });

  test('sign out returns to the login screen and protects routes again', async ({
    loggedInPage: page,
  }) => {
    await page.getByRole('button', { name: /sign out/i }).click();
    await expect(page.getByRole('heading', { name: /sign in/i })).toBeVisible();

    // Deep link is protected again after logout.
    await page.goto('/settings');
    await expect(page.getByRole('heading', { name: /sign in/i })).toBeVisible();
  });

  test('deep link is preserved through the login redirect', async ({ page }) => {
    await page.goto('/settings');
    await expect(page.getByRole('heading', { name: /sign in/i })).toBeVisible();

    await page.getByLabel(/e-mail/i).fill(DEMO_USER.email);
    await page.getByLabel(/password/i).fill(DEMO_USER.password);
    await page.getByRole('button', { name: /sign in/i }).click();

    await expect(page.getByRole('heading', { name: /^settings$/i })).toBeVisible();
    await expect(page).toHaveURL(/\/settings$/);
  });
});
