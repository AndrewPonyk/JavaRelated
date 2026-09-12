import { describe, expect, it } from 'vitest';

import { createRng } from './rng';

describe('createRng (mulberry32)', () => {
  it('is deterministic for the same seed', () => {
    const a = createRng(42);
    const b = createRng(42);
    for (let i = 0; i < 100; i++) {
      expect(a.next()).toBe(b.next());
    }
  });

  it('produces different streams for different seeds', () => {
    const a = createRng(1);
    const b = createRng(2);
    const streamA = Array.from({ length: 10 }, () => a.next());
    const streamB = Array.from({ length: 10 }, () => b.next());
    expect(streamA).not.toEqual(streamB);
  });

  it('int stays inclusive on both bounds and hits them', () => {
    const rng = createRng(7);
    const seen = new Set<number>();
    for (let i = 0; i < 2000; i++) {
      const value = rng.int(-2, 2);
      expect(value).toBeGreaterThanOrEqual(-2);
      expect(value).toBeLessThanOrEqual(2);
      expect(Number.isInteger(value)).toBe(true);
      seen.add(value);
    }
    expect(seen.size).toBe(5); // all of −2…2 observed
  });

  it('intNonZero never returns zero', () => {
    const rng = createRng(11);
    for (let i = 0; i < 1000; i++) {
      expect(rng.intNonZero(-3, 3)).not.toBe(0);
    }
  });

  it('pick returns a member of the list', () => {
    const rng = createRng(13);
    const items = ['a', 'b', 'c'] as const;
    for (let i = 0; i < 50; i++) {
      expect(items).toContain(rng.pick(items));
    }
  });
});
