import { test, expect } from '@playwright/test';

test.describe('Authentication', () => {
  test.beforeEach(async ({ page }) => {
    // Start from the login page
    await page.goto('/login');
  });

  test('should display login form', async ({ page }) => {
    await expect(page).toHaveTitle(/Enterprise Dashboard/);

    // Check form elements are present
    await expect(page.getByTestId('email-input')).toBeVisible();
    await expect(page.getByTestId('password-input')).toBeVisible();
    await expect(page.getByTestId('login-button')).toBeVisible();

    // Check navigation link to register
    await expect(page.getByRole('link', { name: /sign up/i })).toBeVisible();
  });

  test('should show validation errors for empty form', async ({ page }) => {
    await page.click('[data-testid="login-button"]');

    await expect(page.getByText(/email is required/i)).toBeVisible();
    await expect(page.getByText(/password is required/i)).toBeVisible();
  });

  test('should show error for invalid credentials', async ({ page }) => {
    await page.fill('[data-testid="email-input"]', 'invalid@example.com');
    await page.fill('[data-testid="password-input"]', 'wrongpassword');
    await page.click('[data-testid="login-button"]');

    await expect(page.getByText(/invalid email or password/i)).toBeVisible();
  });

  test('should login successfully with valid credentials', async ({ page }) => {
    await page.fill('[data-testid="email-input"]', 'e2e@example.com');
    await page.fill('[data-testid="password-input"]', 'e2epassword123');
    await page.click('[data-testid="login-button"]');

    // Should redirect to dashboard
    await expect(page).toHaveURL(/.*\/dashboard/);

    // Should see welcome message or user info
    await expect(page.getByText(/E2E Test User/i)).toBeVisible();
  });

  test('should navigate to registration page', async ({ page }) => {
    await page.click('a[href="/register"]');

    await expect(page).toHaveURL(/.*\/register/);
    await expect(page.getByTestId('name-input')).toBeVisible();
    await expect(page.getByTestId('email-input')).toBeVisible();
    await expect(page.getByTestId('password-input')).toBeVisible();
    await expect(page.getByTestId('register-button')).toBeVisible();
  });

  test('should register new user', async ({ page }) => {
    await page.goto('/register');

    const timestamp = Date.now();
    const testEmail = `test-${timestamp}@example.com`;

    await page.fill('[data-testid="name-input"]', 'New Test User');
    await page.fill('[data-testid="email-input"]', testEmail);
    await page.fill('[data-testid="password-input"]', 'newpassword123');
    await page.click('[data-testid="register-button"]');

    // Should redirect to dashboard
    await expect(page).toHaveURL(/.*\/dashboard/);
    await expect(page.getByText(/New Test User/i)).toBeVisible();
  });
});

test.describe('Authenticated Navigation', () => {
  test.use({ storageState: './e2e/auth-state.json' });

  test('should logout successfully', async ({ page }) => {
    await page.goto('/dashboard');

    // Open user menu
    await page.click('[data-testid="user-menu-button"]');

    // Click logout
    await page.click('[data-testid="logout-button"]');

    // Should redirect to login page
    await expect(page).toHaveURL(/.*\/login/);

    // Should not be able to access protected routes
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/.*\/login/);
  });
});