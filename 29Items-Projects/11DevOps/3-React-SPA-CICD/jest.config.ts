import type { Config } from 'jest';

const config: Config = {
  // jest-fixed-jsdom = jsdom + the Node globals it strips (fetch, streams, TextEncoder,
  // structuredClone) + Node export conditions so msw/node resolves. Official MSW v2 + Jest
  // recommendation — docs/TECH-NOTES.md §3.6 #2.
  testEnvironment: 'jest-fixed-jsdom',
  roots: ['<rootDir>/src'],
  testMatch: ['**/*.test.{ts,tsx}'],
  transform: {
    '^.+\\.tsx?$': ['ts-jest', { tsconfig: '<rootDir>/tsconfig.jest.json' }],
  },
  moduleNameMapper: {
    // Order matters: the env mock must win over the generic @/ alias.
    // src/app/env.ts uses import.meta.env, which ts-jest (CJS) cannot parse —
    // see docs/TECH-NOTES.md §3.6.
    '^@/app/env$': '<rootDir>/src/test/env.mock.ts',
    '^@/(.*)$': '<rootDir>/src/$1',
    '\\.(css|less|scss|sass)$': 'identity-obj-proxy',
    '\\.(svg|png|jpe?g|gif|webp|avif|woff2?)$': '<rootDir>/src/test/fileMock.ts',
  },
  setupFiles: ['<rootDir>/src/test/polyfills.ts'],
  setupFilesAfterEnv: ['<rootDir>/src/test/setupTests.ts'],
  clearMocks: true,
  restoreMocks: true,
  // Headroom for the raised asyncUtilTimeout (setupTests) under parallel-suite load.
  testTimeout: 15_000,
  collectCoverageFrom: [
    'src/**/*.{ts,tsx}',
    '!src/**/*.d.ts',
    '!src/main.tsx',
    '!src/test/**',
    '!src/app/env.ts', // replaced by env.mock.ts under Jest
  ],
  coverageThreshold: {
    global: {
      statements: 80,
      lines: 80,
      functions: 80,
      branches: 70,
    },
  },
  coverageReporters: ['text-summary', 'lcov', 'cobertura'],
  reporters: ['default'],
};

export default config;
