import { expect, afterEach, vi } from 'vitest';
import { cleanup } from '@testing-library/react';
import matchers from '@testing-library/jest-dom/matchers';
import 'whatwg-fetch';

// Extend Vitest's expect with jest-dom matchers
expect.extend(matchers);

// Clean up after each test
afterEach(() => {
  cleanup();
});

// Mock window.matchMedia
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation(query => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(), // deprecated
    removeListener: vi.fn(), // deprecated
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

// Mock IntersectionObserver
global.IntersectionObserver = vi.fn().mockImplementation(() => ({
  observe: vi.fn(),
  unobserve: vi.fn(),
  disconnect: vi.fn(),
}));

// Mock ResizeObserver
global.ResizeObserver = vi.fn().mockImplementation(() => ({
  observe: vi.fn(),
  unobserve: vi.fn(),
  disconnect: vi.fn(),
}));

// Mock scrollTo
global.scrollTo = vi.fn();

// Mock localStorage
const localStorageMock = {
  getItem: vi.fn(),
  setItem: vi.fn(),
  removeItem: vi.fn(),
  clear: vi.fn(),
};
global.localStorage = localStorageMock as any;

// Mock sessionStorage
const sessionStorageMock = {
  getItem: vi.fn(),
  setItem: vi.fn(),
  removeItem: vi.fn(),
  clear: vi.fn(),
};
global.sessionStorage = sessionStorageMock as any;

// Mock environment variables
vi.mock('~/lib/config', () => ({
  config: {
    apiBaseUrl: 'http://localhost:3001/api',
    wsUrl: 'ws://localhost:3001',
    enableAnalytics: true,
    features: {
      realTimeUpdates: true,
      mlAnalytics: true,
      advancedCharts: true,
    }
  }
}));

// Mock date for consistent testing
const mockDate = new Date('2024-01-15T10:00:00.000Z');
vi.setSystemTime(mockDate);