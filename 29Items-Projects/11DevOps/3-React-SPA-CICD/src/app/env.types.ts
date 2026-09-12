/** Shape shared by the real env (env.ts) and the Jest mock (src/test/env.mock.ts). */
export interface AppEnv {
  /** local | e2e | preview | staging | production */
  readonly envName: string;
  /** Base URL for the Portal API (no trailing slash). */
  readonly apiBaseUrl: string;
  /** Whether the MSW mock backend is active in this build. */
  readonly enableMsw: boolean;
  /** GA4 measurement id; empty string disables analytics entirely. */
  readonly gaMeasurementId: string;
  /** Git SHA in CI builds; 'dev' locally. */
  readonly appVersion: string;
  /** Vite's BASE_URL — '/' normally, '/pr-<n>/' for prefixed PR previews. */
  readonly baseUrl: string;
  readonly isProd: boolean;
}
