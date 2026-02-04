/** @type {import('jest').Config} */
module.exports = {
  preset: 'ts-jest',
  testEnvironment: 'node',

  // Test file patterns
  testMatch: [
    '**/__tests__/**/*.{js,ts}',
    '**/*.{test,spec}.{js,ts}'
  ],

  // Coverage configuration
  collectCoverage: true,
  collectCoverageFrom: [
    'src/**/*.{ts,js}',
    '!src/**/*.d.ts',
    '!src/types/**/*',
    '!src/__tests__/**/*',
    '!src/test/**/*',
  ],

  coverageDirectory: 'coverage',
  coverageReporters: ['text', 'lcov', 'html', 'json'],

  coverageThresholds: {
    global: {
      branches: 80,
      functions: 80,
      lines: 80,
      statements: 80,
    },
  },

  // Setup files
  setupFilesAfterEnv: ['<rootDir>/src/test/setup.ts'],

  // Module resolution
  moduleNameMapping: {
    '^~/(.*)$': '<rootDir>/src/$1',
  },

  // Transform configuration
  transform: {
    '^.+\\.ts$': ['ts-jest', {
      tsconfig: 'tsconfig.json',
    }],
  },

  // Environment variables
  testEnvironmentOptions: {
    NODE_ENV: 'test',
  },

  // Global variables
  globals: {
    'ts-jest': {
      tsconfig: 'tsconfig.json',
    },
  },

  // Timeouts
  testTimeout: 30000,

  // Verbose output
  verbose: true,

  // Clear mocks between tests
  clearMocks: true,
  restoreMocks: true,

  // Detect handles that prevent Jest from exiting
  detectOpenHandles: true,
  forceExit: true,
};