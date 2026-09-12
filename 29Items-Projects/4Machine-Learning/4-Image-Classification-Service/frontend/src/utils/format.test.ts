import { describe, expect, it } from 'vitest';

import { formatScore, validateImageFile } from './format';

describe('formatScore', () => {
  it('formats a fraction as a percentage', () => {
    expect(formatScore(0.9123)).toBe('91.2%');
    expect(formatScore(0)).toBe('0.0%');
    expect(formatScore(1)).toBe('100.0%');
  });

  it('clamps out-of-range values', () => {
    expect(formatScore(1.5)).toBe('100.0%');
    expect(formatScore(-0.2)).toBe('0.0%');
  });
});

describe('validateImageFile', () => {
  const makeFile = (type: string, size: number): File => {
    const blob = new Blob([new Uint8Array(size)], { type });
    return new File([blob], 'test', { type });
  };

  it('accepts a valid jpeg', () => {
    expect(validateImageFile(makeFile('image/jpeg', 1000))).toBeNull();
  });

  it('rejects an unsupported type', () => {
    expect(validateImageFile(makeFile('text/plain', 1000))).toMatch(/Unsupported/);
  });

  it('rejects an empty file', () => {
    expect(validateImageFile(makeFile('image/png', 0))).toMatch(/empty/);
  });

  it('rejects an oversized file', () => {
    expect(validateImageFile(makeFile('image/png', 11 * 1024 * 1024))).toMatch(/too large/);
  });
});
