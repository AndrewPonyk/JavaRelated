import { describe, expect, it } from 'vitest';

import {
  clamp01,
  easeInOutCubic,
  interpolateTransform,
  lerpMat2,
  polarAngle,
} from './interpolation';
import { determinant, IDENTITY, mat2, matApproxEquals, rotation } from './matrix2';

describe('easing', () => {
  it('easeInOutCubic hits the anchor points', () => {
    expect(easeInOutCubic(0)).toBe(0);
    expect(easeInOutCubic(0.5)).toBeCloseTo(0.5, 12);
    expect(easeInOutCubic(1)).toBe(1);
  });

  it('clamp01 clamps', () => {
    expect(clamp01(-2)).toBe(0);
    expect(clamp01(0.3)).toBeCloseTo(0.3, 12);
    expect(clamp01(7)).toBe(1);
  });
});

describe('polarAngle', () => {
  it('recovers the angle of a pure rotation', () => {
    for (const theta of [0, 0.5, Math.PI / 2, 2.5, -1.2]) {
      expect(polarAngle(rotation(theta))).toBeCloseTo(theta, 9);
    }
  });
});

describe('interpolateTransform', () => {
  it('matches the endpoints', () => {
    const from = mat2(2, 1, 0, 1);
    const to = rotation(1.1);
    expect(matApproxEquals(interpolateTransform(from, to, 0), from)).toBe(true);
    expect(matApproxEquals(interpolateTransform(from, to, 1), to)).toBe(true);
  });

  it('animates I → rotation(π) as a rotation, never collapsing the plane', () => {
    const to = rotation(Math.PI);
    const mid = interpolateTransform(IDENTITY, to, 0.5);
    expect(matApproxEquals(mid, rotation(Math.PI / 2))).toBe(true);
    for (const t of [0.1, 0.25, 0.5, 0.75, 0.9]) {
      expect(determinant(interpolateTransform(IDENTITY, to, t))).toBeCloseTo(1, 6);
    }
    // Entrywise lerp would have collapsed at the midpoint — the very bug this fixes.
    expect(determinant(lerpMat2(IDENTITY, to, 0.5))).toBeCloseTo(0, 9);
  });

  it('falls back to entrywise lerp for orientation-reversing targets', () => {
    const reflect = mat2(1, 0, 0, -1); // det = −1
    const mid = interpolateTransform(IDENTITY, reflect, 0.5);
    expect(matApproxEquals(mid, lerpMat2(IDENTITY, reflect, 0.5))).toBe(true);
  });
});
