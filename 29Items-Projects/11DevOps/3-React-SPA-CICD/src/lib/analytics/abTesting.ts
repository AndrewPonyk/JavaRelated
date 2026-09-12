import { useEffect, useMemo } from 'react';

import { trackEvent } from './analytics';

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * A/B experimentation — the frontend's half of the ML regression loop
 * (docs/ARCHITECTURE.md §2.3).
 *
 * The frontend guarantees exactly two things:
 *   1. DETERMINISTIC ASSIGNMENT — hash(experiment, subject) → variant. Same user,
 *      same variant, every session, no storage needed. Unit-tested for stability
 *      and distribution (abTesting.test.ts).
 *   2. FAITHFUL EXPOSURE LOGGING — `experiment_exposure` fires when the variant UI
 *      actually renders (useExperiment), not at assignment time. Assignment-time
 *      logging inflates exposure and biases the regression.
 *
 * Everything statistical is OFFLINE: GA4 → BigQuery export → nightly regression
 * (outcome ~ variant + covariates, CUPED variance reduction) → decisions land back
 * here as registry updates (or, later, remote config).
 * ═══════════════════════════════════════════════════════════════════════════
 */

export interface ExperimentVariant {
  id: string;
  /** Relative traffic weight; weights are normalized across the experiment. */
  weight: number;
}

export interface Experiment {
  id: string;
  description: string;
  variants: ExperimentVariant[];
}

/**
 * Active experiment registry — editing this file (a reviewed PR) is the experiment
 * console. When allocation changes need to skip the deploy cycle, layer a remote-config
 * fetch over this registry (it then becomes the offline fallback) — Phase 3 roadmap item
 * in docs/PROJECT-PLAN.md.
 */
export const EXPERIMENTS = {
  'dashboard-layout-v2': {
    id: 'dashboard-layout-v2',
    description: 'Card grid (control) vs condensed list (treatment) on the dashboard',
    variants: [
      { id: 'control', weight: 50 },
      { id: 'treatment', weight: 50 },
    ],
  },
  'onboarding-checklist': {
    id: 'onboarding-checklist',
    description: 'Show a first-run checklist on the dashboard',
    variants: [
      { id: 'control', weight: 90 },
      { id: 'treatment', weight: 10 }, // canary allocation
    ],
  },
} as const satisfies Record<string, Experiment>;

export type ExperimentId = keyof typeof EXPERIMENTS;

/** FNV-1a 32-bit — tiny, dependency-free, uniform enough for traffic bucketing. */
export function hashSubject(input: string): number {
  let hash = 0x811c9dc5;
  for (let i = 0; i < input.length; i++) {
    hash ^= input.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193);
  }
  return hash >>> 0;
}

/**
 * Deterministic variant assignment. The subject is salted with the experiment id so
 * assignments across experiments are uncorrelated (a user's bucket in one experiment
 * tells you nothing about their bucket in another).
 */
export function getVariant(experimentId: ExperimentId, subjectId: string): string {
  const experiment = EXPERIMENTS[experimentId];
  const totalWeight = experiment.variants.reduce((sum, v) => sum + v.weight, 0);

  const bucket = (hashSubject(`${experimentId}:${subjectId}`) / 0x1_0000_0000) * totalWeight;

  let cumulative = 0;
  for (const variant of experiment.variants) {
    cumulative += variant.weight;
    if (bucket < cumulative) return variant.id;
  }
  // Unreachable given weights > 0; satisfies the type system.
  return experiment.variants[experiment.variants.length - 1]!.id;
}

/**
 * Assign + log exposure once per (experiment, subject) per app lifetime.
 * Use at the render site of the variant UI.
 */
const loggedExposures = new Set<string>();

export function useExperiment(experimentId: ExperimentId, subjectId: string): string {
  const variantId = useMemo(() => getVariant(experimentId, subjectId), [experimentId, subjectId]);

  useEffect(() => {
    const key = `${experimentId}:${subjectId}`;
    if (loggedExposures.has(key)) return;
    loggedExposures.add(key);
    trackEvent('experiment_exposure', { experiment_id: experimentId, variant_id: variantId });
  }, [experimentId, subjectId, variantId]);

  return variantId;
}

/** Test seam: clears the exposure dedup set. */
export function resetExposureLogForTests(): void {
  loggedExposures.clear();
}
