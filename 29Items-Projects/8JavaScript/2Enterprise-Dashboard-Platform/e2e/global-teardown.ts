import { FullConfig } from '@playwright/test';
import fs from 'fs';

async function globalTeardown(config: FullConfig) {
  console.log('🧹 Cleaning up E2E tests...');

  // Remove auth state file
  try {
    if (fs.existsSync('./e2e/auth-state.json')) {
      fs.unlinkSync('./e2e/auth-state.json');
    }
  } catch (error) {
    console.warn('Could not remove auth state file:', error);
  }

  // Additional cleanup can be added here
  // For example, cleanup test data from database

  console.log('✅ E2E cleanup complete');
}

export default globalTeardown;