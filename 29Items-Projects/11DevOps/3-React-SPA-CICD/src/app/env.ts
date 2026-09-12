import type { AppEnv } from './env.types';

/**
 * The ONLY module allowed to touch import.meta.env.
 *
 * Why: ts-jest compiles to CommonJS, where `import.meta` is a syntax error. Jest replaces
 * this module with src/test/env.mock.ts via moduleNameMapper (jest.config.ts), so as long
 * as env access goes through here, tests never parse this file.
 * See docs/TECH-NOTES.md §3.6 #1.
 */
export const env: AppEnv = {
  envName: import.meta.env.VITE_ENV_NAME ?? 'local',
  apiBaseUrl: (import.meta.env.VITE_API_BASE_URL ?? '/api').replace(/\/$/, ''),
  enableMsw: import.meta.env.VITE_ENABLE_MSW === 'true',
  gaMeasurementId: import.meta.env.VITE_GA_MEASUREMENT_ID ?? '',
  appVersion: import.meta.env.VITE_APP_VERSION ?? 'dev',
  baseUrl: import.meta.env.BASE_URL,
  isProd: import.meta.env.PROD,
};
