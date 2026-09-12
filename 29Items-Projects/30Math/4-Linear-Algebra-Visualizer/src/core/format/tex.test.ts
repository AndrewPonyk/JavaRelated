import { describe, expect, it } from 'vitest';

import { eigen } from '../math/eigen';
import { mat2, rotation, scaling } from '../math/matrix2';
import { vec2 } from '../math/vector2';
import {
  determinantToTex,
  eigenToTex,
  formatNumber,
  matrixToTex,
  vectorToTex,
  vectorToTexInline,
} from './tex';

describe('formatNumber', () => {
  it('trims trailing zeros and normalizes -0', () => {
    expect(formatNumber(1.5)).toBe('1.5');
    expect(formatNumber(2)).toBe('2');
    expect(formatNumber(1.23456, 3)).toBe('1.235');
    expect(formatNumber(-0.0000001)).toBe('0');
  });
});

describe('structure formatting', () => {
  it('renders vectors and matrices as pmatrix blocks', () => {
    expect(vectorToTex(vec2(1, -2))).toContain('\\begin{pmatrix} 1 \\\\ -2 \\end{pmatrix}');
    expect(matrixToTex(mat2(1, 2, 3, 4))).toBe('\\begin{pmatrix} 1 & 2 \\\\ 3 & 4 \\end{pmatrix}');
    expect(vectorToTexInline(vec2(0.5, 3))).toBe('(0.5,\\; 3)');
  });

  it('reads the determinant geometrically', () => {
    expect(determinantToTex(scaling(2))).toContain('\\det(A) = 4');
    expect(determinantToTex(mat2(1, 0, 0, -1))).toContain('orientation flipped');
    expect(determinantToTex(scaling(2))).not.toContain('orientation flipped');
  });
});

describe('eigenToTex', () => {
  it('describes distinct real eigenvalues with directions', () => {
    const tex = eigenToTex(eigen(mat2(2, 1, 1, 2)));
    expect(tex).toContain('\\lambda_1 = 3');
    expect(tex).toContain('\\lambda_2 = 1');
    expect(tex).toContain('\\text{ along }');
  });

  it('describes complex pairs as a rotation with angle and scale', () => {
    const tex = eigenToTex(eigen(rotation(Math.PI / 2)));
    expect(tex).toContain('\\pm');
    expect(tex).toContain('rotation by');
    expect(tex).toContain('90');
  });

  it('distinguishes scalar from defective repeated eigenvalues', () => {
    expect(eigenToTex(eigen(scaling(2)))).toContain('every direction');
    expect(eigenToTex(eigen(mat2(1, 1, 0, 1)))).toContain('repeated');
  });
});
