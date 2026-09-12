import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

// RTL auto-cleanup only registers itself when test globals are enabled;
// we keep globals off (explicit imports), so clean up manually.
afterEach(() => {
  cleanup();
});
