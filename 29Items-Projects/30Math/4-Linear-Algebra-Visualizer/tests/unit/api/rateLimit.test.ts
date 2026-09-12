// @vitest-environment node
import { beforeEach, describe, expect, it } from 'vitest';

import { checkRateLimit, resetRateLimiter } from '../../../api/_lib/rateLimit';

describe('checkRateLimit', () => {
  beforeEach(() => {
    resetRateLimiter();
  });

  it('allows 30 requests per minute per key, then blocks', () => {
    const t0 = 1_000_000;
    for (let i = 0; i < 30; i++) {
      expect(checkRateLimit('key-a', t0 + i)).toBe(true);
    }
    expect(checkRateLimit('key-a', t0 + 100)).toBe(false);
  });

  it('tracks keys independently', () => {
    const t0 = 1_000_000;
    for (let i = 0; i < 30; i++) checkRateLimit('key-a', t0 + i);
    expect(checkRateLimit('key-a', t0 + 50)).toBe(false);
    expect(checkRateLimit('key-b', t0 + 50)).toBe(true);
  });

  it('frees budget once the window slides past old requests', () => {
    const t0 = 1_000_000;
    for (let i = 0; i < 30; i++) checkRateLimit('key-a', t0 + i);
    expect(checkRateLimit('key-a', t0 + 1000)).toBe(false);
    // 61 s later the original burst has left the window.
    expect(checkRateLimit('key-a', t0 + 61_000)).toBe(true);
  });
});
