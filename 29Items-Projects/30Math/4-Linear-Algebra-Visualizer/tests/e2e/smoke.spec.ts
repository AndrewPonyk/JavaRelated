import { expect, test } from '@playwright/test';

/**
 * E2E against `vite preview` (static build, no /api): the app must boot, render
 * WebGL, and the exercise loop must work end-to-end in offline mode.
 * Deliberately DOM-state assertions, not pixels (TECH-NOTES §3.6 pitfall 5).
 */

test('application shell renders with canvas and controls', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByRole('heading', { name: /linear algebra visualizer/i })).toBeVisible();
  await expect(page.locator('canvas.vector-canvas')).toBeVisible();
  await expect(page.getByRole('group', { name: /matrix entries/i })).toBeVisible();
  await expect(page.getByRole('button', { name: /rotate 90/i })).toBeVisible();
});

test('applying a preset updates the eigen and determinant readouts', async ({ page }) => {
  await page.goto('/');

  const eigenPanel = page.locator('section[aria-label="Eigen analysis"]');
  await expect(eigenPanel).toBeVisible();

  // Rotation has complex eigenvalues — the readout must say so.
  await page.getByRole('button', { name: /rotate 90/i }).click();
  await expect(eigenPanel).toContainText(/rotation/, { timeout: 5_000 });

  // Reflection flips orientation — det readout must call it out.
  await page.getByRole('button', { name: /reflect x/i }).click();
  await expect(eigenPanel).toContainText(/orientation flipped/, { timeout: 5_000 });
});

test('exercise panel degrades gracefully without an API', async ({ page }) => {
  await page.goto('/');

  // vite preview has no /api ⇒ explicit error state with recovery actions.
  await expect(page.getByRole('button', { name: /continue offline/i })).toBeVisible({
    timeout: 15_000,
  });
  await expect(page.getByRole('button', { name: /retry/i })).toBeVisible();
});

test('full offline exercise flow: answer, feedback, next', async ({ page }) => {
  await page.goto('/');

  await page.getByRole('button', { name: /continue offline/i }).click({ timeout: 15_000 });
  await expect(page.getByRole('button', { name: /submit/i })).toBeVisible();

  // Empty answer is rejected inline.
  await page.getByRole('button', { name: /submit/i }).click();
  await expect(page.getByRole('status')).toContainText(/valid number/i);

  // A (deliberately wrong) numeric answer gets graded with feedback.
  const inputs = page.locator('.answer-inputs input');
  const count = await inputs.count();
  for (let i = 0; i < count; i++) {
    await inputs.nth(i).fill('999999');
  }
  await page.getByRole('button', { name: /submit/i }).click();
  await expect(page.getByRole('status')).toContainText(/not quite/i);

  // Next exercise clears feedback and inputs.
  await page.getByRole('button', { name: /next exercise/i }).click();
  await expect(page.getByRole('status')).toHaveCount(0);
  await expect(inputs.first()).toHaveValue('');
});

test('user vectors can be added and removed', async ({ page }) => {
  await page.goto('/');

  await page.getByRole('button', { name: /add vector/i }).click();
  const list = page.getByRole('list', { name: /user vectors/i });
  await expect(list.getByRole('listitem')).toHaveCount(1);
  await expect(list).toContainText('v1');

  await page.getByRole('button', { name: /remove v1/i }).click();
  await expect(page.getByRole('list', { name: /user vectors/i })).toHaveCount(0);
});
