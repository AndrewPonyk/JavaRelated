import { describe, expect, it } from 'vitest';

import {
  apply,
  determinant,
  IDENTITY,
  inverse,
  mat2,
  matApproxEquals,
  multiply,
  rotation,
  scaling,
  shear,
} from './matrix2';
import { vec2, vecApproxEquals } from './vector2';

describe('matrix2', () => {
  it('identity leaves vectors unchanged', () => {
    expect(vecApproxEquals(apply(IDENTITY, vec2(3, -7)), vec2(3, -7))).toBe(true);
  });

  it('applies the documented row-major convention (columns are images of î, ĵ)', () => {
    const m = mat2(1, 2, 3, 4);
    expect(apply(m, vec2(1, 0))).toEqual(vec2(1, 3)); // first column
    expect(apply(m, vec2(0, 1))).toEqual(vec2(2, 4)); // second column
  });

  it('rotation preserves area (det = 1) and rotates î to ĵ at 90°', () => {
    const r = rotation(Math.PI / 2);
    expect(determinant(r)).toBeCloseTo(1, 12);
    expect(vecApproxEquals(apply(r, vec2(1, 0)), vec2(0, 1))).toBe(true);
  });

  it('multiply composes: (m·n)v = m(n(v))', () => {
    const m = shear(2);
    const n = scaling(3, -1);
    const v = vec2(2, 5);
    expect(vecApproxEquals(apply(multiply(m, n), v), apply(m, apply(n, v)))).toBe(true);
  });

  it('inverse round-trips to the identity', () => {
    const m = mat2(2, 1, 1, 1);
    const mInv = inverse(m);
    expect(mInv).not.toBeNull();
    expect(matApproxEquals(multiply(m, mInv!), IDENTITY)).toBe(true);
  });

  it('inverse of a singular matrix is null, not an exception', () => {
    expect(inverse(mat2(1, 2, 2, 4))).toBeNull();
  });
});
