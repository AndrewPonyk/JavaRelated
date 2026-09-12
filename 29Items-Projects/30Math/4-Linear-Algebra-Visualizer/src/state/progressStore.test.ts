import { beforeEach, describe, expect, it } from 'vitest';

import { defaultProgress } from '@/core/exercises/difficulty';
import { mergeWithDefaults, useProgressStore } from './progressStore';

describe('progressStore', () => {
  beforeEach(() => {
    localStorage.clear();
    useProgressStore.getState().resetAll();
  });

  it('starts with defaults for every topic', () => {
    expect(useProgressStore.getState().progress).toEqual(defaultProgress());
    expect(useProgressStore.getState().streak).toBe(0);
  });

  it('applyAttempt raises mastery and extends the streak on correct answers', () => {
    const store = useProgressStore.getState();
    store.applyAttempt('vectors', true);
    store.applyAttempt('vectors', true);

    const vectors = useProgressStore.getState().progress.find((p) => p.topic === 'vectors');
    expect(vectors?.attempts).toBe(2);
    expect(vectors?.mastery).toBeCloseTo(0.15 + 0.15 * 0.85, 9);
    expect(useProgressStore.getState().streak).toBe(2);
  });

  it('a wrong answer cuts mastery and resets the streak', () => {
    const store = useProgressStore.getState();
    store.applyAttempt('vectors', true);
    store.applyAttempt('vectors', false);
    expect(useProgressStore.getState().streak).toBe(0);
    const vectors = useProgressStore.getState().progress.find((p) => p.topic === 'vectors');
    expect(vectors?.mastery).toBeCloseTo(0.15 * 0.7, 9);
  });

  it('setFromServer merges server rows over defaults (server wins)', () => {
    useProgressStore.getState().applyAttempt('vectors', true);
    useProgressStore
      .getState()
      .setFromServer([{ topic: 'determinant', mastery: 0.6, attempts: 9 }]);

    const state = useProgressStore.getState();
    expect(state.hydratedFromServer).toBe(true);
    expect(state.progress.find((p) => p.topic === 'determinant')?.mastery).toBe(0.6);
    // Topics the server doesn't know about fall back to defaults.
    expect(state.progress.find((p) => p.topic === 'vectors')?.mastery).toBe(0);
    expect(state.progress).toHaveLength(defaultProgress().length);
  });

  it('reconcile replaces the optimistic row with the server-confirmed one', () => {
    useProgressStore.getState().applyAttempt('vectors', true);
    useProgressStore.getState().reconcile({ topic: 'vectors', mastery: 0.5, attempts: 42 });
    const vectors = useProgressStore.getState().progress.find((p) => p.topic === 'vectors');
    expect(vectors).toEqual({ topic: 'vectors', mastery: 0.5, attempts: 42 });
  });

  it('persists progress to localStorage', () => {
    useProgressStore.getState().applyAttempt('vectors', true);
    const cached = localStorage.getItem('lav.progress');
    expect(cached).toBeTruthy();
    expect(cached).toContain('vectors');
  });

  it('mergeWithDefaults drops nothing and defaults everything missing', () => {
    const merged = mergeWithDefaults([{ topic: 'eigenvalues', mastery: 0.4, attempts: 2 }]);
    expect(merged).toHaveLength(defaultProgress().length);
    expect(merged.find((p) => p.topic === 'eigenvalues')?.attempts).toBe(2);
  });
});
