import { setupWorker } from 'msw/browser';

import { handlers } from './handlers';

// Browser worker: local dev, hermetic E2E builds, PR previews. Started from src/main.tsx
// only when VITE_ENABLE_MSW=true (dynamic import — never in staging/prod bundles).
// Requires public/mockServiceWorker.js — generate once with `npm run msw:init`.
export const worker = setupWorker(...handlers);
