import { create } from 'zustand';
import { persist } from 'zustand/middleware';

import { defaultProgress, updateMastery } from '@/core/exercises/difficulty';
import { TOPICS, type Topic, type TopicProgress } from '@/core/exercises/types';

/**
 * Shared learner-progress state, cached in localStorage (offline-first).
 *
 * Flow: on boot the exercise panel flushes queued attempts, then hydrates this
 * store from GET /api/progress (server wins — it has just absorbed the queue).
 * Each graded attempt updates the store optimistically; when the server
 * confirms an attempt it returns the authoritative row, which `reconcile`
 * writes back.
 */

/** Server rows merged over local defaults so every topic always exists. */
export function mergeWithDefaults(remote: readonly TopicProgress[]): TopicProgress[] {
  const byTopic = new Map(remote.map((p) => [p.topic, p]));
  return defaultProgress().map((fallback) => byTopic.get(fallback.topic) ?? fallback);
}

export interface ProgressState {
  progress: TopicProgress[];
  /** Consecutive correct answers in this session. */
  streak: number;
  hydratedFromServer: boolean;

  setFromServer: (remote: readonly TopicProgress[]) => void;
  /** Optimistic local update after grading (same rule the server applies). */
  applyAttempt: (topic: Topic, correct: boolean) => void;
  /** Server-confirmed row wins over the optimistic value. */
  reconcile: (row: TopicProgress) => void;
  resetAll: () => void;
}

export const useProgressStore = create<ProgressState>()(
  persist(
    (set) => ({
      progress: defaultProgress(),
      streak: 0,
      hydratedFromServer: false,

      setFromServer: (remote) =>
        set({ progress: mergeWithDefaults(remote), hydratedFromServer: true }),
      applyAttempt: (topic, correct) =>
        set((s) => ({
          progress: s.progress.map((p) => (p.topic === topic ? updateMastery(p, correct) : p)),
          streak: correct ? s.streak + 1 : 0,
        })),
      reconcile: (row) =>
        set((s) => ({
          progress: s.progress.map((p) => (p.topic === row.topic ? row : p)),
        })),
      resetAll: () => set({ progress: defaultProgress(), streak: 0, hydratedFromServer: false }),
    }),
    {
      name: 'lav.progress',
      partialize: (s) => ({ progress: s.progress, streak: s.streak }),
      // Drop unknown topics from older cached shapes instead of crashing.
      merge: (persisted, current) => {
        const cached = persisted as Partial<Pick<ProgressState, 'progress' | 'streak'>> | undefined;
        const valid = (cached?.progress ?? []).filter((p) =>
          (TOPICS as readonly string[]).includes(p.topic),
        );
        return {
          ...current,
          progress: mergeWithDefaults(valid),
          streak: cached?.streak ?? 0,
        };
      },
    },
  ),
);
