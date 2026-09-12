import { expect, test } from "@playwright/test";

test("builder home renders", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Marketing Website Builder" })).toBeVisible();
});

