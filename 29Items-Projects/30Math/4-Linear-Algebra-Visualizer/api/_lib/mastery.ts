/**
 * Server-side mastery update rule — MUST mirror src/core/exercises/difficulty.ts
 * (deliberate 6-line duplication; api/ and src/ stay decoupled, PROJECT-PLAN §1.1).
 *
 * Asymmetric EMA: a correct answer closes 15 % of the remaining gap to 1,
 * a wrong answer costs 30 % of current mastery.
 */

const GAIN = 0.15;
const LOSS = 0.3;

export const nextMastery = (current: number, correct: boolean): number => {
  const updated = correct ? current + GAIN * (1 - current) : current * (1 - LOSS);
  return Math.min(1, Math.max(0, updated));
};
