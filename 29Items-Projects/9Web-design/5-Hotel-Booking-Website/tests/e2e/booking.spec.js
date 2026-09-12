import { expect, test } from "@playwright/test";

test("booking page renders search form", async ({ page }) => {
  await page.goto("/");

  await expect(page.getByRole("heading", { name: /find and book/i })).toBeVisible();
  await expect(page.getByRole("button", { name: /check availability/i })).toBeVisible();
});
