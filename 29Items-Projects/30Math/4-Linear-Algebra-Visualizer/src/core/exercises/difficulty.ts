import { clamp01 } from '../math/interpolation';
import { TOPICS, type Difficulty, type Topic, type TopicProgress } from './types';

/**
 * Mastery model + curriculum progression.
 *
 * Mastery per topic ∈ [0, 1], updated as an asymmetric exponential moving average:
 * correct answers close 15 % of the remaining gap to 1; a wrong answer costs 30 %
 * of current mastery. Wrong answers hurt more than right answers help — mastery
 * must be earned repeatedly, and difficulty backs off quickly when the learner
 * struggles.
 *
 * The SAME update rule runs server-side (api/_lib/mastery.ts) so persisted
 * mastery never trusts the client — keep the constants in sync.
 */

export const GAIN = 0.15;
export const LOSS = 0.3;

/** A topic is considered "passed" for progression once mastery reaches this gate. */
export const MASTERY_GATE = 0.8;

export const defaultProgress = (): TopicProgress[] =>
  TOPICS.map((topic) => ({ topic, mastery: 0, attempts: 0 }));

export const nextMastery = (current: number, correct: boolean): number =>
  clamp01(correct ? current + GAIN * (1 - current) : current * (1 - LOSS));

export const updateMastery = (progress: TopicProgress, correct: boolean): TopicProgress => ({
  ...progress,
  attempts: progress.attempts + 1,
  mastery: nextMastery(progress.mastery, correct),
});

/** Mastery 0–0.2 → difficulty 1, …, 0.8–1 → difficulty 5. */
export const masteryToDifficulty = (mastery: number): Difficulty =>
  Math.max(1, Math.min(5, 1 + Math.floor(clamp01(mastery) * 5))) as Difficulty;

/**
 * Curriculum policy: work topics in order, moving on once a topic passes the
 * gate; when everything is gated, review the weakest topic.
 */
export function chooseNext(progressList: readonly TopicProgress[]): {
  topic: Topic;
  difficulty: Difficulty;
} {
  const byTopic = new Map(progressList.map((p) => [p.topic, p]));
  const inOrder = TOPICS.map((topic) => byTopic.get(topic) ?? { topic, mastery: 0, attempts: 0 });

  const current = inOrder.find((p) => p.mastery < MASTERY_GATE);
  const chosen =
    current ?? inOrder.reduce((weakest, p) => (p.mastery < weakest.mastery ? p : weakest));

  return { topic: chosen.topic, difficulty: masteryToDifficulty(chosen.mastery) };
}
