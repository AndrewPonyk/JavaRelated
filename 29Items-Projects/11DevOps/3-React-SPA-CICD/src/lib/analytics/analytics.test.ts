import {
  getStoredConsent,
  initAnalytics,
  isAnalyticsConfigured,
  resetAnalyticsForTests,
  setAnalyticsConsent,
  setUserId,
  trackEvent,
  trackPageView,
} from './analytics';

import { setTestEnv } from '@/test/env.mock';

// This suite exercises the CONFIGURED path — give the (mutable) test env a measurement id.
beforeEach(() => {
  setTestEnv({ gaMeasurementId: 'G-TEST123', appVersion: 'test-sha' });
});

type DataLayerEntry = unknown[];

function dataLayer(): DataLayerEntry[] {
  return (window.dataLayer ?? []) as DataLayerEntry[];
}

function entriesOfType(type: string): DataLayerEntry[] {
  return dataLayer().filter((entry) => entry[0] === type);
}

afterEach(() => {
  resetAnalyticsForTests();
  delete window.dataLayer;
  delete window.gtag;
  document.head.querySelectorAll('script').forEach((s) => s.remove());
});

describe('analytics (configured)', () => {
  it('reports itself as configured', () => {
    expect(isAnalyticsConfigured()).toBe(true);
  });

  it('is a safe no-op before initAnalytics()', () => {
    trackEvent('page_view', { page_path: '/x' });
    expect(window.dataLayer).toBeUndefined();
  });

  it('initializes with consent denied by default and does NOT load gtag.js', () => {
    initAnalytics();

    const consentDefaults = entriesOfType('consent');
    expect(consentDefaults).toHaveLength(1);
    expect(consentDefaults[0]).toEqual([
      'consent',
      'default',
      expect.objectContaining({ analytics_storage: 'denied' }),
    ]);

    expect(entriesOfType('config')).toHaveLength(1);
    expect(document.head.querySelector('script[src*="googletagmanager"]')).toBeNull();
  });

  it('is idempotent: repeated init does not duplicate config', () => {
    initAnalytics();
    initAnalytics();
    expect(entriesOfType('config')).toHaveLength(1);
  });

  it('stamps events with app_version and environment', () => {
    initAnalytics();
    trackEvent('login_success', { method: 'password' });

    const events = entriesOfType('event');
    expect(events).toHaveLength(1);
    expect(events[0]).toEqual([
      'event',
      'login_success',
      expect.objectContaining({
        method: 'password',
        app_version: 'test-sha',
        environment: 'test',
      }),
    ]);
  });

  it('tracks page views through the typed event map', () => {
    initAnalytics();
    trackPageView('/settings');

    expect(entriesOfType('event')[0]).toEqual([
      'event',
      'page_view',
      expect.objectContaining({ page_path: '/settings' }),
    ]);
  });

  it('granting consent persists it, updates gtag, and injects the script', () => {
    initAnalytics();
    setAnalyticsConsent('granted');

    expect(getStoredConsent()).toBe('granted');
    expect(dataLayer()).toContainEqual([
      'consent',
      'update',
      expect.objectContaining({ analytics_storage: 'granted' }),
    ]);

    const script = document.head.querySelector<HTMLScriptElement>(
      'script[src*="googletagmanager"]',
    );
    expect(script?.src).toContain('G-TEST123');
  });

  it('denying consent persists it and never injects the script', () => {
    initAnalytics();
    setAnalyticsConsent('denied');

    expect(getStoredConsent()).toBe('denied');
    expect(document.head.querySelector('script[src*="googletagmanager"]')).toBeNull();
  });

  it('applies previously stored consent at init (returning visitor)', () => {
    window.localStorage.setItem('portal.analytics-consent', 'granted');
    initAnalytics();

    expect(document.head.querySelector('script[src*="googletagmanager"]')).not.toBeNull();
  });

  it('associates the pseudonymous user id via config', () => {
    initAnalytics();
    setUserId('user-42');

    expect(entriesOfType('config')).toHaveLength(2);
    expect(entriesOfType('config')[1]).toEqual([
      'config',
      'G-TEST123',
      expect.objectContaining({ user_id: 'user-42' }),
    ]);
  });
});
