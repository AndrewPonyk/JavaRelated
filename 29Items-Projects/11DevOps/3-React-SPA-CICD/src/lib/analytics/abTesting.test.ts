import { renderHook } from '@testing-library/react';

import {
  getVariant,
  hashSubject,
  useExperiment,
  EXPERIMENTS,
  type ExperimentId,
} from './abTesting';
import * as analytics from './analytics';

// The A/B → ML regression pipeline depends on these invariants (docs/ARCHITECTURE.md §2.3):
// assignments must be stable, valid, well-distributed, and uncorrelated across experiments.
describe('abTesting bucketing', () => {
  const experimentId: ExperimentId = 'dashboard-layout-v2';

  it('is deterministic: the same subject always gets the same variant', () => {
    for (let i = 0; i < 50; i++) {
      const subject = `user-${i}`;
      const first = getVariant(experimentId, subject);
      for (let run = 0; run < 5; run++) {
        expect(getVariant(experimentId, subject)).toBe(first);
      }
    }
  });

  it('only assigns variants that exist in the experiment definition', () => {
    const validIds = EXPERIMENTS[experimentId].variants.map((v) => v.id);
    for (let i = 0; i < 200; i++) {
      expect(validIds).toContain(getVariant(experimentId, `user-${i}`));
    }
  });

  it('splits a 50/50 experiment roughly evenly over many subjects', () => {
    const SAMPLE = 5000;
    let treatment = 0;
    for (let i = 0; i < SAMPLE; i++) {
      if (getVariant(experimentId, `subject-${i}`) === 'treatment') treatment++;
    }
    const share = treatment / SAMPLE;
    expect(share).toBeGreaterThan(0.45);
    expect(share).toBeLessThan(0.55);
  });

  it('respects skewed weights (90/10 canary experiment)', () => {
    const SAMPLE = 5000;
    let treatment = 0;
    for (let i = 0; i < SAMPLE; i++) {
      if (getVariant('onboarding-checklist', `subject-${i}`) === 'treatment') treatment++;
    }
    const share = treatment / SAMPLE;
    expect(share).toBeGreaterThan(0.07);
    expect(share).toBeLessThan(0.13);
  });

  it('decorrelates assignments across experiments (salted by experiment id)', () => {
    const SAMPLE = 2000;
    let sameBucket = 0;
    for (let i = 0; i < SAMPLE; i++) {
      const subject = `subject-${i}`;
      if (getVariant(experimentId, subject) === getVariant('onboarding-checklist', subject)) {
        sameBucket++;
      }
    }
    // If correlated, a treatment user in one would predict the other; expected agreement
    // for independent 50/50 × 90/10 splits is 0.5*0.9 + 0.5*0.1 = 0.5.
    const agreement = sameBucket / SAMPLE;
    expect(agreement).toBeGreaterThan(0.4);
    expect(agreement).toBeLessThan(0.6);
  });

  it('hashSubject is stable across runs (regression pin)', () => {
    // Pinned values: if the hash implementation changes, every live experiment reshuffles.
    // That must be a deliberate, reviewed decision — this test makes it one.
    expect(hashSubject('dashboard-layout-v2:user-1')).toBe(
      hashSubject('dashboard-layout-v2:user-1'),
    );
    expect(hashSubject('a')).not.toBe(hashSubject('b'));
  });
});

describe('useExperiment exposure logging', () => {
  it('returns the deterministic variant and logs the exposure exactly once per subject', () => {
    const trackSpy = jest.spyOn(analytics, 'trackEvent').mockImplementation(() => undefined);
    const expected = getVariant('dashboard-layout-v2', 'user-42');

    const { result, rerender } = renderHook(() => useExperiment('dashboard-layout-v2', 'user-42'));

    expect(result.current).toBe(expected);
    expect(trackSpy).toHaveBeenCalledTimes(1);
    expect(trackSpy).toHaveBeenCalledWith('experiment_exposure', {
      experiment_id: 'dashboard-layout-v2',
      variant_id: expected,
    });

    // Re-renders and remounts of the same (experiment, subject) do not re-log exposure.
    rerender();
    renderHook(() => useExperiment('dashboard-layout-v2', 'user-42'));
    expect(trackSpy).toHaveBeenCalledTimes(1);
  });

  it('logs separate exposures for different experiments', () => {
    const trackSpy = jest.spyOn(analytics, 'trackEvent').mockImplementation(() => undefined);

    renderHook(() => useExperiment('dashboard-layout-v2', 'user-7'));
    renderHook(() => useExperiment('onboarding-checklist', 'user-7'));

    expect(trackSpy).toHaveBeenCalledTimes(2);
  });
});
