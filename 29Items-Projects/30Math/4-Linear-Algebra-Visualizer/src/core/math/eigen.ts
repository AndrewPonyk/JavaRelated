import { determinant, trace, type Mat2 } from './matrix2';
import { EPSILON, normalize, vec2, type Vec2 } from './vector2';

/**
 * Eigen decomposition of a 2×2 real matrix via the characteristic polynomial
 *   λ² − tr(A)·λ + det(A) = 0.
 *
 * The result is a discriminated union — callers must handle all three regimes,
 * because each one is a different geometric story the visualizer tells:
 *  - realDistinct: two invariant lines (scaled by λ₁, λ₂)
 *  - realRepeated: one invariant line (shear-like, defective) or "everything scales" (λI)
 *  - complex:      no invariant line — the map rotates (spiral for |λ| ≠ 1)
 */

export interface EigenPair {
  readonly value: number;
  /** Unit eigenvector. Direction is canonical only up to sign — compare with `areColinear`. */
  readonly vector: Vec2;
}

export type EigenResult =
  | { kind: 'realDistinct'; pairs: readonly [EigenPair, EigenPair] }
  | { kind: 'realRepeated'; value: number; vector: Vec2; geometricMultiplicity: 1 | 2 }
  | { kind: 'complex'; real: number; imaginary: number };

/**
 * Eigenvector for a known eigenvalue λ of A, from (A − λI)v = 0.
 * If b ≠ 0, v = (b, λ − a) satisfies both rows (second row vanishes because λ is a root
 * of the characteristic polynomial). Symmetrically for c ≠ 0; otherwise A is diagonal.
 */
const eigenvectorFor = (m: Mat2, lambda: number): Vec2 => {
  if (Math.abs(m.b) > EPSILON) return normalize(vec2(m.b, lambda - m.a));
  if (Math.abs(m.c) > EPSILON) return normalize(vec2(lambda - m.d, m.c));
  // Diagonal matrix: the eigenvectors are the coordinate axes.
  return Math.abs(m.a - lambda) <= EPSILON ? vec2(1, 0) : vec2(0, 1);
};

export function eigen(m: Mat2): EigenResult {
  const tr = trace(m);
  const det = determinant(m);
  const discriminant = tr * tr - 4 * det;
  // Scale-relative epsilon so the distinct/repeated/complex classification
  // stays stable for very large or very small matrix entries.
  const eps = EPSILON * Math.max(1, tr * tr, Math.abs(4 * det));

  if (discriminant > eps) {
    const sqrtDisc = Math.sqrt(discriminant);
    const lambda1 = (tr + sqrtDisc) / 2;
    const lambda2 = (tr - sqrtDisc) / 2;
    return {
      kind: 'realDistinct',
      pairs: [
        { value: lambda1, vector: eigenvectorFor(m, lambda1) },
        { value: lambda2, vector: eigenvectorFor(m, lambda2) },
      ],
    };
  }

  if (discriminant >= -eps) {
    const lambda = tr / 2;
    const isScalarMultipleOfIdentity =
      Math.abs(m.b) <= EPSILON && Math.abs(m.c) <= EPSILON && Math.abs(m.a - m.d) <= EPSILON;
    return {
      kind: 'realRepeated',
      value: lambda,
      vector: eigenvectorFor(m, lambda),
      geometricMultiplicity: isScalarMultipleOfIdentity ? 2 : 1,
    };
  }

  return { kind: 'complex', real: tr / 2, imaginary: Math.sqrt(-discriminant) / 2 };
}
