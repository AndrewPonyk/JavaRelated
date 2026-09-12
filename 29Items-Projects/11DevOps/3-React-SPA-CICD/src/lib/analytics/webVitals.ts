import {
  onCLS,
  onFCP,
  onINP,
  onLCP,
  onTTFB,
  type CLSMetric,
  type FCPMetric,
  type INPMetric,
  type LCPMetric,
  type TTFBMetric,
} from 'web-vitals';

import { trackEvent } from './analytics';

// The five vitals we subscribe to — deliberately excludes the deprecated FID.
type ReportedMetric = CLSMetric | FCPMetric | INPMetric | LCPMetric | TTFBMetric;

/**
 * Real-user monitoring: Core Web Vitals from actual sessions → GA4 → BigQuery.
 * Complements the lab-only Lighthouse CI budgets (docs/ARCHITECTURE.md §2.4) —
 * lab data gates merges, field data tells the truth.
 */
export function reportWebVitals(): void {
  const report = (metric: ReportedMetric): void => {
    trackEvent('web_vital', {
      metric: metric.name,
      // CLS is unitless ×1000 for integer-friendly aggregation; others are ms.
      value: Math.round(metric.name === 'CLS' ? metric.value * 1000 : metric.value),
      rating: metric.rating,
      page_path: window.location.pathname,
    });
  };

  onCLS(report);
  onINP(report);
  onLCP(report);
  onFCP(report);
  onTTFB(report);
}
