import { expect, test } from '@playwright/test'

test('loads the inventory workspace and supports responsive navigation', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Inventory Management' })).toBeVisible()
  await expect(page.getByRole('navigation', { name: 'Primary' })).toBeVisible()
  await expect(page.getByRole('form', { name: 'Barcode lookup' })).toBeVisible()
})
