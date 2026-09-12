import type { AppEnv } from '@/app/env.types';

/**
 * Stands in for src/app/env.ts under Jest (moduleNameMapper in jest.config.ts) because
 * the real module reads import.meta.env, which ts-jest's CJS output cannot parse.
 * The AppEnv interface keeps the two in lockstep at compile time.
 *
 * Tests that need different config (e.g. analytics with a measurement id) use
 * setTestEnv() in a beforeEach; setupTests resets to defaults after every test.
 */
const defaults: AppEnv = {
  envName: 'test',
  apiBaseUrl: 'http://localhost/api',
  enableMsw: false, // Jest uses mocks/server.ts directly, not the browser worker
  gaMeasurementId: '', // analytics disabled by default in unit tests
  appVersion: 'test',
  baseUrl: '/',
  isProd: false,
};

export const env: AppEnv = { ...defaults };

export function setTestEnv(overrides: Partial<AppEnv>): void {
  Object.assign(env as Record<string, unknown>, overrides);
}

export function resetTestEnv(): void {
  Object.assign(env as Record<string, unknown>, defaults);
}
