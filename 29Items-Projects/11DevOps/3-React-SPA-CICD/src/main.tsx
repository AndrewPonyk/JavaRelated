import React from 'react';
import ReactDOM from 'react-dom/client';

import { App } from './App';
import { env } from '@/app/env';
import { initAnalytics } from '@/lib/analytics/analytics';
import { reportWebVitals } from '@/lib/analytics/webVitals';
import { logger } from '@/lib/logger';
import '@/styles/global.css';

async function bootstrap(): Promise<void> {
  // Mock backend for local dev / hermetic E2E / PR previews. The dynamic import keeps
  // MSW entirely out of staging/production bundles.
  if (env.enableMsw) {
    const { worker } = await import('../mocks/browser');
    await worker.start({
      // BASE_URL-aware so the worker resolves under /pr-<n>/ preview prefixes too.
      serviceWorker: { url: `${env.baseUrl}mockServiceWorker.js` },
      onUnhandledRequest: 'bypass',
    });
    logger.info('MSW mock backend enabled');
  }

  initAnalytics();
  reportWebVitals();

  // Last-resort telemetry: nothing escapes unrecorded (docs/ARCHITECTURE.md §2.6).
  window.addEventListener('error', (event) => {
    logger.error('Uncaught error', { message: event.message });
  });
  window.addEventListener('unhandledrejection', (event) => {
    logger.error('Unhandled promise rejection', { reason: String(event.reason) });
  });

  const container = document.getElementById('root');
  if (!container) throw new Error('Root container #root not found');

  ReactDOM.createRoot(container).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>,
  );
}

// A failed bootstrap (e.g. the MSW worker script missing in a mock-enabled build) must
// surface as a readable message, never as a silent white screen.
bootstrap().catch((error: unknown) => {
  logger.error('Application bootstrap failed', { error: String(error) });
  const container = document.getElementById('root');
  if (container) {
    container.innerHTML = ''; // drop any half-rendered markup
    const panel = document.createElement('div');
    panel.setAttribute('role', 'alert');
    panel.className = 'error-panel';
    panel.textContent =
      'The portal failed to start. Please reload the page — if the problem persists, contact support.';
    container.appendChild(panel);
  }
});
