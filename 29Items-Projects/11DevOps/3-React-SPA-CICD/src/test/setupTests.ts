import '@testing-library/jest-dom';
import { configure } from '@testing-library/react';

import { resetTestEnv } from './env.mock';

// findBy*/waitFor poll until found, so raising the ceiling doesn't slow passing tests —
// it only stops timing flakes when 16 suites compete for CPU on a loaded runner.
configure({ asyncUtilTimeout: 4000 });
import { server } from '../../mocks/server';
import { resetDb } from '../../mocks/db/store';
import { tokenStore } from '@/api/httpClient';
import { resetExposureLogForTests } from '@/lib/analytics/abTesting';

// MSW lifecycle: every test runs against the mock API with pristine seed data.
// 'error' on unhandled requests keeps tests honest — no accidental real network,
// no silently unmocked endpoint.
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));

afterEach(() => {
  server.resetHandlers();
  resetDb();
  resetTestEnv();
  tokenStore.clear();
  resetExposureLogForTests();
  window.localStorage.clear(); // consent + onboarding-dismissal state
});

afterAll(() => server.close());
