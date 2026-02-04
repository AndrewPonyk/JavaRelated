import { chromium, FullConfig } from '@playwright/test';

async function globalSetup(config: FullConfig) {
  const { baseURL } = config.projects[0].use;

  // Launch browser for setup
  const browser = await chromium.launch();
  const page = await browser.newPage();

  try {
    // Wait for the application to be ready
    console.log('🚀 Setting up E2E tests...');

    // Check if frontend is accessible
    await page.goto(baseURL!);
    await page.waitForSelector('[data-testid="app-ready"]', { timeout: 30000 });

    // Check if backend API is accessible
    const response = await page.request.get(`${baseURL!.replace(':3000', ':3001')}/health`);
    if (!response.ok()) {
      throw new Error('Backend is not ready');
    }

    // Create test user for E2E tests
    const registerResponse = await page.request.post(`${baseURL!.replace(':3000', ':3001')}/api/auth/register`, {
      data: {
        name: 'E2E Test User',
        email: 'e2e@example.com',
        password: 'e2epassword123',
      },
    });

    if (!registerResponse.ok()) {
      const existingUserResponse = await page.request.post(`${baseURL!.replace(':3000', ':3001')}/api/auth/login`, {
        data: {
          email: 'e2e@example.com',
          password: 'e2epassword123',
        },
      });

      if (!existingUserResponse.ok()) {
        console.log('⚠️ Could not create or login test user, tests may fail');
      }
    }

    // Store authentication state
    await page.goto(`${baseURL!}/login`);
    await page.fill('[data-testid="email-input"]', 'e2e@example.com');
    await page.fill('[data-testid="password-input"]', 'e2epassword123');
    await page.click('[data-testid="login-button"]');

    // Wait for successful login
    await page.waitForURL('**/dashboard', { timeout: 10000 });

    // Save signed-in state
    await page.context().storageState({ path: './e2e/auth-state.json' });

    console.log('✅ E2E setup complete');

  } catch (error) {
    console.error('❌ E2E setup failed:', error);
    throw error;
  } finally {
    await browser.close();
  }
}

export default globalSetup;