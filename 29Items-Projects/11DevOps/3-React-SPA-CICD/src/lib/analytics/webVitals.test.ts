import type { Metric } from 'web-vitals';

import { reportWebVitals } from './webVitals';
import * as analytics from './analytics';

type MetricCallback = (metric: Metric) => void;

const callbacks: Record<string, MetricCallback> = {};

jest.mock('web-vitals', () => ({
  onCLS: (cb: MetricCallback) => {
    callbacks.CLS = cb;
  },
  onFCP: (cb: MetricCallback) => {
    callbacks.FCP = cb;
  },
  onINP: (cb: MetricCallback) => {
    callbacks.INP = cb;
  },
  onLCP: (cb: MetricCallback) => {
    callbacks.LCP = cb;
  },
  onTTFB: (cb: MetricCallback) => {
    callbacks.TTFB = cb;
  },
}));

function fakeMetric(name: Metric['name'], value: number, rating: Metric['rating']): Metric {
  return { name, value, rating } as Metric;
}

describe('reportWebVitals', () => {
  it('subscribes to all five vitals and forwards rounded samples to analytics', () => {
    const trackSpy = jest.spyOn(analytics, 'trackEvent').mockImplementation(() => undefined);

    reportWebVitals();
    expect(Object.keys(callbacks).sort()).toEqual(['CLS', 'FCP', 'INP', 'LCP', 'TTFB']);

    callbacks.LCP!(fakeMetric('LCP', 2400.4, 'good'));
    expect(trackSpy).toHaveBeenCalledWith('web_vital', {
      metric: 'LCP',
      value: 2400,
      rating: 'good',
      page_path: '/',
    });
  });

  it('scales the unitless CLS value ×1000 for integer aggregation', () => {
    const trackSpy = jest.spyOn(analytics, 'trackEvent').mockImplementation(() => undefined);

    reportWebVitals();
    callbacks.CLS!(fakeMetric('CLS', 0.1234, 'needs-improvement'));

    expect(trackSpy).toHaveBeenCalledWith('web_vital', {
      metric: 'CLS',
      value: 123,
      rating: 'needs-improvement',
      page_path: '/',
    });
  });
});
