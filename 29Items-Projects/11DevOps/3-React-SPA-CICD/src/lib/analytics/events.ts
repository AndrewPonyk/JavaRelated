/**
 * Typed analytics event dictionary — the single reviewable contract for what leaves the app.
 *
 * Rules (docs/ARCHITECTURE.md §2.5):
 *  - Payloads carry ids and enums, NEVER free-form PII (no names, e-mails, addresses).
 *  - New events are added HERE first; ad-hoc trackEvent('stringly', …) does not compile.
 *  - snake_case names/params to match GA4 conventions and the BigQuery export schema
 *    consumed by the ML regression pipeline.
 */
export interface AnalyticsEventMap {
  login_attempt: { method: 'password' };
  login_success: { method: 'password' };
  login_failure: { method: 'password'; reason: string };

  page_view: { page_path: string };

  /** Fired when a variant's UI actually renders — the exposure record for A/B regression. */
  experiment_exposure: { experiment_id: string; variant_id: string };

  settings_saved: { fields_changed: number };

  /** Real-user performance sample; complements lab Lighthouse runs. */
  web_vital: {
    metric: 'LCP' | 'CLS' | 'INP' | 'FCP' | 'TTFB';
    value: number;
    rating: 'good' | 'needs-improvement' | 'poor';
    page_path: string;
  };
}

export type AnalyticsEventName = keyof AnalyticsEventMap;
