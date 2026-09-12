import { expect, test } from '@playwright/test';

test('edits and exports a local image without an image upload request', async ({ page }) => {
  const networkRequests: string[] = [];
  page.on('request', (request) => {
    if (!request.url().startsWith('http://127.0.0.1:4173')) networkRequests.push(request.url());
  });

  await page.goto('/');
  const fixture = await page.evaluate(async () => {
    const canvas = document.createElement('canvas');
    canvas.width = 8;
    canvas.height = 8;
    const context = canvas.getContext('2d');
    if (!context) throw new Error('Canvas is unavailable.');
    context.fillStyle = '#4477cc';
    context.fillRect(0, 0, 8, 8);
    const blob = await new Promise<Blob>((resolve, reject) =>
      canvas.toBlob(
        (result) => (result ? resolve(result) : reject(new Error('PNG encoding failed.'))),
        'image/png',
      ),
    );
    return [...new Uint8Array(await blob.arrayBuffer())];
  });
  await page.locator('input[type="file"]').setInputFiles({
    name: 'fixture.png',
    mimeType: 'image/png',
    buffer: Buffer.from(fixture),
  });
  await expect(page.getByLabel('Edited image preview')).toBeVisible();
  await page.getByLabel('Effect').selectOption('brightness');
  await page.getByRole('button', { name: 'Apply effect' }).click();
  await expect(page.getByText('Processing image…')).toBeHidden();
  await page.getByRole('button', { name: 'Auto-crop' }).click();
  await expect(page.getByText('Processing image…')).toBeHidden();

  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Export image' }).click();
  expect((await download).suggestedFilename()).toMatch(/fixture-edited\.webp$/);
  expect(networkRequests).toEqual([]);
});
