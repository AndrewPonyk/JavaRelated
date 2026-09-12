const { test, expect } = require("@playwright/test");

test("home page renders featured travel content", async ({ page }) => {
  await page.goto("/");

  await expect(page.getByRole("heading", { name: "Northstar Travel" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Explore Tours" })).toBeVisible();
});

test("tours page exposes filters", async ({ page }) => {
  await page.goto("/src/pages/tours.html");

  await expect(page.locator("#regionFilter")).toBeVisible();
  await expect(page.locator("#tourList")).toBeVisible();
  await page.locator("#regionFilter").selectOption("asia");
  await expect(page.getByText("Kyoto Culture Route")).toBeVisible();
});

test("destinations page renders the map container", async ({ page }) => {
  await page.goto("/src/pages/destinations.html");

  await expect(page.locator("#destinationMap")).toBeVisible();
  await expect(page.locator(".leaflet-container")).toBeVisible();
});

test("contact form validates and submits an inquiry", async ({ page }) => {
  await page.goto("/src/pages/contact.html");

  await page.getByRole("button", { name: "Send inquiry" }).click();
  await expect(page.locator("#formStatus")).toContainText("Please complete the required fields.");

  await page.locator("#name").fill("Jordan Client");
  await page.locator("#email").fill("jordan@example.com");
  await page.locator("#destination").fill("Kyoto");
  await page.locator("#message").fill("We want a culture-focused trip for four travelers.");
  await page.locator("#consent").check();
  await page.getByRole("button", { name: "Send inquiry" }).click();

  await expect(page.locator("#formStatus")).toContainText("Inquiry received.");
});
