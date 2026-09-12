import { setupServer } from 'msw/node';

import { handlers } from './handlers';

// Node server: Jest tests (lifecycle in src/test/setupTests.ts).
export const server = setupServer(...handlers);
