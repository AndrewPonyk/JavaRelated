import type { AnalyticsEventMap, AnalyticsEventName } from './events';
import { env } from '@/app/env';
import { logger } from '@/lib/logger';

/**
 * GA4 (gtag.js) wrapper. Design constraints (docs/TECH-NOTES.md §3.6 #9):
 *  - Analytics is LOSSY by design: ad-blockers eat 20–40 % of events. Nothing in the app
 *    may depend on gtag loading — every call no-ops safely.
 *  - Disabled entirely when VITE_GA_MEASUREMENT_ID is empty (local/e2e/preview builds).
 *  - Consent-first: gtag.js is only injected after the user grants consent
 *    (ConsentBanner → setAnalyticsConsent). Until then events accumulate in the local
 *    dataLayer and are processed when/if the script loads.
 */

declare global {
  interface Window {
    dataLayer?: unknown[];
    gtag?: (...args: unknown[]) => void;
  }
}

export type ConsentDecision = 'granted' | 'denied';

const CONSENT_KEY = 'portal.analytics-consent';

let initialized = false;
let scriptInjected = false;

export function isAnalyticsConfigured(): boolean {
  return env.gaMeasurementId !== '';
}

/** The persisted consent decision, or null when the user hasn't decided yet. */
export function getStoredConsent(): ConsentDecision | null {
  try {
    const value = window.localStorage.getItem(CONSENT_KEY);
    return value === 'granted' || value === 'denied' ? value : null;
  } catch {
    return null; // storage blocked → treat as undecided (banner shows, nothing tracked)
  }
}

function injectGtagScript(): void {
  if (scriptInjected) return;
  scriptInjected = true;

  const script = document.createElement('script');
  script.async = true;
  script.src = `https://www.googletagmanager.com/gtag/js?id=${env.gaMeasurementId}`;
  script.onerror = () => logger.debug('gtag.js failed to load (blocked?) — events will no-op');
  document.head.appendChild(script);
}

export function initAnalytics(): void {
  if (!isAnalyticsConfigured()) {
    logger.debug('Analytics disabled (no measurement id for this environment)');
    return;
  }
  if (initialized) return;
  initialized = true;

  window.dataLayer = window.dataLayer ?? [];
  window.gtag = function gtag(...args: unknown[]) {
    window.dataLayer?.push(args);
  };

  // Consent Mode defaults: nothing is stored until the user opts in via the banner.
  window.gtag('consent', 'default', {
    ad_storage: 'denied',
    ad_user_data: 'denied',
    ad_personalization: 'denied',
    analytics_storage: 'denied',
  });

  window.gtag('js', new Date());
  window.gtag('config', env.gaMeasurementId, {
    send_page_view: false, // SPA page views are tracked explicitly (AppLayout)
    app_version: env.appVersion,
    environment: env.envName,
  });

  // Returning visitor who already consented → apply it and load the script.
  if (getStoredConsent() === 'granted') {
    applyConsent('granted');
  }
}

function applyConsent(decision: ConsentDecision): void {
  if (!initialized || !window.gtag) return;
  window.gtag('consent', 'update', {
    analytics_storage: decision,
  });
  if (decision === 'granted') {
    injectGtagScript();
  }
}

/** Called by the ConsentBanner; persists the decision and applies it to gtag. */
export function setAnalyticsConsent(decision: ConsentDecision): void {
  try {
    window.localStorage.setItem(CONSENT_KEY, decision);
  } catch {
    // storage blocked — the decision applies for this session only
  }
  applyConsent(decision);
}

/** Type-safe event tracking: names and payloads must exist in AnalyticsEventMap. */
export function trackEvent<E extends AnalyticsEventName>(
  name: E,
  params: AnalyticsEventMap[E],
): void {
  if (!initialized || !window.gtag) return; // analytics is never a dependency
  window.gtag('event', name, { ...params, app_version: env.appVersion, environment: env.envName });
}

export function trackPageView(pagePath: string): void {
  trackEvent('page_view', { page_path: pagePath });
}

/** Pseudonymous id linking events to experiment subjects — never PII. */
export function setUserId(userId: string | null): void {
  if (!initialized || !window.gtag) return;
  window.gtag('config', env.gaMeasurementId, { user_id: userId ?? undefined });
}

/** Test seam: resets module state (Jest runs share the module registry per file). */
export function resetAnalyticsForTests(): void {
  initialized = false;
  scriptInjected = false;
}
