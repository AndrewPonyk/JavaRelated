import { describe, expect, it } from 'vitest';

import { eigen } from './eigen';
import { mat2, rotation, scaling, shear } from './matrix2';
import { areColinear, vec2 } from './vector2';

describe('eigen', () => {
  it('solves the classic symmetric case [[2,1],[1,2]] → λ = 3, 1', () => {
    const result = eigen(mat2(2, 1, 1, 2));
    expect(result.kind).toBe('realDistinct');
    if (result.kind !== 'realDistinct') return;

    const [p1, p2] = result.pairs;
    expect(p1.value).toBeCloseTo(3, 9);
    expect(p2.value).toBeCloseTo(1, 9);
    // Eigenvectors are only defined up to scale/sign — compare by colinearity.
    expect(areColinear(p1.vector, vec2(1, 1))).toBe(true);
    expect(areColinear(p2.vector, vec2(1, -1))).toBe(true);
  });

  it('classifies a rotation as complex (no real invariant line)', () => {
    const result = eigen(rotation(Math.PI / 3));
    expect(result.kind).toBe('complex');
    if (result.kind !== 'complex') return;
    expect(result.real).toBeCloseTo(Math.cos(Math.PI / 3), 9);
    expect(result.imaginary).toBeCloseTo(Math.sin(Math.PI / 3), 9);
  });

  it('classifies a shear as repeated & defective (one invariant line)', () => {
    const result = eigen(shear(1));
    expect(result.kind).toBe('realRepeated');
    if (result.kind !== 'realRepeated') return;
    expect(result.value).toBeCloseTo(1, 9);
    expect(result.geometricMultiplicity).toBe(1);
    expect(areColinear(result.vector, vec2(1, 0))).toBe(true);
  });

  it('classifies uniform scaling as repeated with full eigenspace', () => {
    const result = eigen(scaling(2));
    expect(result.kind).toBe('realRepeated');
    if (result.kind !== 'realRepeated') return;
    expect(result.value).toBeCloseTo(2, 9);
    expect(result.geometricMultiplicity).toBe(2);
  });

  it('handles diagonal matrices with distinct entries (b = c = 0 branch)', () => {
    const result = eigen(scaling(3, -2));
    expect(result.kind).toBe('realDistinct');
    if (result.kind !== 'realDistinct') return;
    expect(result.pairs[0].value).toBeCloseTo(3, 9);
    expect(result.pairs[1].value).toBeCloseTo(-2, 9);
    expect(areColinear(result.pairs[0].vector, vec2(1, 0))).toBe(true);
    expect(areColinear(result.pairs[1].vector, vec2(0, 1))).toBe(true);
  });
});
