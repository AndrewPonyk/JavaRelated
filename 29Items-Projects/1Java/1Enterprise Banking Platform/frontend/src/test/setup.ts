import '@testing-library/jest-dom';
import { beforeAll, afterEach, afterAll } from 'vitest';

// Mock window.matchMedia
beforeAll(() => {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }),
  });
});

// Clean up after each test
afterEach(() => {
  // Clear all mocks
});

// Final cleanup
afterAll(() => {
  // Any global cleanup
});
