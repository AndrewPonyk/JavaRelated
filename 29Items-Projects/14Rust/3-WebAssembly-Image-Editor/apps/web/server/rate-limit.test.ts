// @vitest-environment node

import { describe, expect, it } from 'vitest';

import { FixedWindowRateLimiter } from './rate-limit';

describe('FixedWindowRateLimiter', () => {
  it('limits each key and permits it again after the window resets', () => {
    let now = 1_000;
    const limiter = new FixedWindowRateLimiter(2, 60_000, () => now);
    expect(limiter.allow('client-a')).toBe(true);
    expect(limiter.allow('client-a')).toBe(true);
    expect(limiter.allow('client-a')).toBe(false);
    expect(limiter.allow('client-b')).toBe(true);
    now += 60_000;
    expect(limiter.allow('client-a')).toBe(true);
  });
});
