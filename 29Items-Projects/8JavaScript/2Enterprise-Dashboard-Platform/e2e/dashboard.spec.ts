import { test, expect } from '@playwright/test';

test.describe('Dashboard', () => {
  test.use({ storageState: './e2e/auth-state.json' });

  test.beforeEach(async ({ page }) => {
    await page.goto('/dashboard');
  });

  test('should display dashboard layout', async ({ page }) => {
    await expect(page).toHaveTitle(/Dashboard.*Enterprise/);

    // Check main dashboard elements
    await expect(page.getByTestId('dashboard-header')).toBeVisible();
    await expect(page.getByTestId('dashboard-sidebar')).toBeVisible();
    await expect(page.getByTestId('dashboard-content')).toBeVisible();

    // Check navigation items
    await expect(page.getByRole('link', { name: /overview/i })).toBeVisible();
    await expect(page.getByRole('link', { name: /analytics/i })).toBeVisible();
    await expect(page.getByRole('link', { name: /reports/i })).toBeVisible();
  });

  test('should display performance metrics', async ({ page }) => {
    // Wait for metrics to load
    await expect(page.getByTestId('performance-metrics')).toBeVisible();

    // Check for metric cards
    await expect(page.getByText(/CPU Usage/i)).toBeVisible();
    await expect(page.getByText(/Memory Usage/i)).toBeVisible();
    await expect(page.getByText(/Disk Usage/i)).toBeVisible();
    await expect(page.getByText(/Network/i)).toBeVisible();
  });

  test('should display charts', async ({ page }) => {
    // Wait for charts to load
    await expect(page.getByTestId('performance-chart')).toBeVisible();
    await expect(page.getByTestId('trends-chart')).toBeVisible();

    // Check chart controls
    await expect(page.getByRole('button', { name: /1h/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /24h/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /7d/i })).toBeVisible();
  });

  test('should change time range', async ({ page }) => {
    // Click different time range
    await page.click('[data-testid="timerange-24h"]');

    // Wait for chart to update
    await page.waitForTimeout(1000);

    // Chart should be visible and updated
    await expect(page.getByTestId('performance-chart')).toBeVisible();
  });

  test('should display AI insights widget', async ({ page }) => {
    await expect(page.getByTestId('ai-insights-widget')).toBeVisible();

    // Check for insights content
    const insightsWidget = page.getByTestId('ai-insights-widget');
    await expect(insightsWidget.getByText(/insights/i)).toBeVisible();
  });

  test('should refresh data', async ({ page }) => {
    // Click refresh button
    await page.click('[data-testid="refresh-button"]');

    // Should show loading state briefly
    await expect(page.getByTestId('loading-indicator')).toBeVisible();

    // Loading should disappear
    await expect(page.getByTestId('loading-indicator')).not.toBeVisible();
  });

  test('should navigate to analytics page', async ({ page }) => {
    await page.click('a[href="/analytics"]');

    await expect(page).toHaveURL(/.*\/analytics/);
    await expect(page.getByText(/Analytics Dashboard/i)).toBeVisible();
  });

  test('should display anomaly detection', async ({ page }) => {
    await expect(page.getByTestId('anomaly-detection-widget')).toBeVisible();

    const anomalyWidget = page.getByTestId('anomaly-detection-widget');
    await expect(anomalyWidget.getByText(/anomaly/i)).toBeVisible();
  });

  test('should handle responsive layout', async ({ page }) => {
    // Test mobile viewport
    await page.setViewportSize({ width: 375, height: 667 });

    // Sidebar should be collapsed
    await expect(page.getByTestId('dashboard-sidebar')).not.toBeVisible();

    // Menu button should be visible
    await expect(page.getByTestId('mobile-menu-button')).toBeVisible();

    // Open mobile menu
    await page.click('[data-testid="mobile-menu-button"]');
    await expect(page.getByTestId('mobile-navigation')).toBeVisible();
  });
});

test.describe('Dashboard Customization', () => {
  test.use({ storageState: './e2e/auth-state.json' });

  test('should open dashboard settings', async ({ page }) => {
    await page.goto('/dashboard');

    // Click settings button
    await page.click('[data-testid="dashboard-settings-button"]');

    // Settings modal should open
    await expect(page.getByTestId('dashboard-settings-modal')).toBeVisible();
  });

  test('should toggle auto-refresh', async ({ page }) => {
    await page.goto('/dashboard');

    // Open settings
    await page.click('[data-testid="dashboard-settings-button"]');

    // Toggle auto-refresh
    await page.click('[data-testid="auto-refresh-toggle"]');

    // Close modal
    await page.click('[data-testid="settings-close-button"]');

    // Setting should be applied (this would depend on actual implementation)
  });
});