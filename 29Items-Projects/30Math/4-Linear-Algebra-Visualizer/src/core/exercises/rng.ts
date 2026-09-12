/**
 * Deterministic seeded PRNG (mulberry32). Same seed ⇒ same exercise, always —
 * this is what makes exercises reproducible from the (topic, difficulty, seed)
 * triple stored in the database, and what makes generator tests meaningful.
 */

export interface Rng {
  /** Uniform float in [0, 1). */
  next(): number;
  /** Uniform integer in [min, max], inclusive on both ends. */
  int(min: number, max: number): number;
  /** Uniform non-zero integer in [min, max]. `min ≤ 0 ≤ max` required with min < max. */
  intNonZero(min: number, max: number): number;
  pick<T>(items: readonly T[]): T;
}

export function createRng(seed: number): Rng {
  let state = seed >>> 0;

  const next = (): number => {
    state = (state + 0x6d2b79f5) | 0;
    let t = Math.imul(state ^ (state >>> 15), 1 | state);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };

  const int = (min: number, max: number): number => min + Math.floor(next() * (max - min + 1));

  const intNonZero = (min: number, max: number): number => {
    // Rejection sampling; ranges here are tiny so this terminates immediately.
    let value = int(min, max);
    while (value === 0) value = int(min, max);
    return value;
  };

  return {
    next,
    int,
    intNonZero,
    pick: <T>(items: readonly T[]): T => items[int(0, items.length - 1)],
  };
}
