import {
  initAnalytics,
  isAnalyticsConfigured,
  resetAnalyticsForTests,
  trackEvent,
} from './analytics';

// Default test env has no measurement id (src/test/env.mock.ts) — the DISABLED path:
// local, e2e, and preview builds ship with analytics fully off.
describe('analytics (not configured)', () => {
  afterEach(() => resetAnalyticsForTests());

  it('reports itself as not configured', () => {
    expect(isAnalyticsConfigured()).toBe(false);
  });

  it('initAnalytics is a no-op: no dataLayer, no gtag, no script', () => {
    initAnalytics();

    expect(window.dataLayer).toBeUndefined();
    expect(window.gtag).toBeUndefined();
    expect(document.head.querySelector('script[src*="googletagmanager"]')).toBeNull();
  });

  it('trackEvent never throws even when fully disabled', () => {
    expect(() => trackEvent('page_view', { page_path: '/' })).not.toThrow();
  });
});
