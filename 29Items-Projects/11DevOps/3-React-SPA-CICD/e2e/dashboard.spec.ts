import { expect, test } from './fixtures/auth.fixture';

test.describe('dashboard', () => {
  test('shows the account summary cards from the API', async ({ loggedInPage: page }) => {
    const summary = page.getByRole('region', { name: /account summary/i });
    await expect(summary).toBeVisible();

    await expect(summary.getByRole('heading', { name: 'Balance', exact: true })).toBeVisible();
    await expect(summary.getByRole('heading', { name: /open tickets/i })).toBeVisible();
    await expect(summary.getByRole('heading', { name: /last sign-in/i })).toBeVisible();
    await expect(summary.getByRole('heading', { name: /balance trend/i })).toBeVisible();
  });

  test('shows the balance trend and recent activity', async ({ loggedInPage: page }) => {
    await expect(page.getByRole('img', { name: /balance (up|down)/i })).toBeVisible();
    await expect(page.getByRole('heading', { name: /recent activity/i })).toBeVisible();
    await expect(page.getByText(/account top-up/i)).toBeVisible();
  });

  test('shows announcements', async ({ loggedInPage: page }) => {
    await expect(page.getByRole('heading', { name: /announcements/i })).toBeVisible();
    await expect(page.getByRole('article').first()).toBeVisible();
  });

  test('navigates to settings and saves a change end-to-end', async ({ loggedInPage: page }) => {
    await page.getByRole('link', { name: /settings/i }).click();
    await expect(page.getByRole('heading', { name: /^settings$/i })).toBeVisible();

    const displayName = page.getByLabel(/display name/i);
    await displayName.fill('Renamed Customer');
    await page.getByRole('button', { name: /save changes/i }).click();

    await expect(page.getByRole('status')).toContainText(/saved/i);
  });
});
