import { expect, test } from '@playwright/test';

test('homepage renders key sections', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByRole('heading', { name: 'Aster Table' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Seasonal favorites' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Plan your evening.' })).toBeVisible();
});

test('reservation form shows validation feedback', async ({ page }) => {
  await page.goto('/#reservation');
  await page.getByRole('button', { name: 'Request reservation' }).click();

  await expect(page.locator('[data-reservation-status]')).not.toBeEmpty();
});

test('menu filters and gallery lightbox are interactive', async ({ page }) => {
  await page.goto('/#menu');

  await page.getByRole('button', { name: 'desserts' }).click();
  const menuPreview = page.locator('[data-menu-preview]');
  await expect(menuPreview.getByRole('heading', { name: 'Citrus Posset' })).toBeVisible();
  await expect(menuPreview.getByRole('heading', { name: 'Market Fish' })).toBeHidden();

  await page.goto('/#gallery');
  await page.locator('[data-lightbox-src="/assets/images/gallery-1.svg"]').click();
  await expect(page.locator('[data-lightbox]')).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.locator('[data-lightbox]')).toBeHidden();
});

test('valid reservation submits to the API', async ({ page }) => {
  await page.route('**/api/reservations', async (route) => {
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({
        ok: true,
        reservation: {
          id: '00000000-0000-0000-0000-000000000001',
          status: 'requested'
        }
      })
    });
  });

  await page.goto('/#reservation');
  await page.getByLabel('Name').fill('Grace Hopper');
  await page.getByLabel('Email').fill('grace@example.com');
  await page.getByLabel('Date').fill(futureDate());
  await page.getByLabel('Party size').fill('2');
  await page.getByRole('button', { name: 'Request reservation' }).click();

  await expect(page.locator('[data-reservation-status]')).toContainText('Request received');
});

function futureDate() {
  const date = new Date();
  date.setDate(date.getDate() + 3);
  return date.toISOString().slice(0, 10);
}
