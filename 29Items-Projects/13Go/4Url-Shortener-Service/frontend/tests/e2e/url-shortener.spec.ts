import { expect, test } from "@playwright/test";

const pngPixel = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=",
  "base64"
);

test("creates, previews, and opens a short URL", async ({ page }) => {
  await page.route("**/api/v1/urls?limit=20", async (route) => {
    await route.fulfill({ json: { items: [], limit: 20 } });
  });
  await page.route("**/api/v1/urls", async (route) => {
    await route.fulfill({
      status: 201,
      json: {
        id: "1",
        shortCode: "article",
        shortUrl: "http://localhost:8080/article",
        originalUrl: "https://example.com/article",
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString()
      }
    });
  });
  await page.route("**/api/v1/urls/article/qr", async (route) => {
    await route.fulfill({ body: pngPixel, contentType: "image/png" });
  });

  await page.goto("/");
  await page.getByLabel("Destination URL").fill("https://example.com/article");
  await page.getByLabel("Custom code").fill("article");
  await page.getByRole("button", { name: /Shorten/i }).click();

  await expect(page.getByText("http://localhost:8080/article")).toBeVisible();
  await expect(page.getByAltText("QR code for http://localhost:8080/article")).toBeVisible();
});
