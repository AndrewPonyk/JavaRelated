import { determinant, mat2, multiply, rotation, type Mat2 } from './matrix2';
import { EPSILON, vec2, type Vec2 } from './vector2';

/** Easing/interpolation helpers used by the rendering layer's matrix tween. */

export const clamp01 = (t: number): number => Math.min(1, Math.max(0, t));

export const lerp = (from: number, to: number, t: number): number => from + (to - from) * t;

/** Smooth start & stop — the "3Blue1Brown feel" for transform animations. */
export const easeInOutCubic = (t: number): number =>
  t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;

export const lerpVec2 = (from: Vec2, to: Vec2, t: number): Vec2 =>
  vec2(lerp(from.x, to.x, t), lerp(from.y, to.y, t));

/**
 * Entrywise matrix interpolation. Fine for stretches/shears, but I → rotation(π)
 * passes through the zero matrix (visual collapse). Kept as the fallback for
 * orientation-reversing or singular endpoints; `interpolateTransform` below is
 * what the animator actually uses.
 */
export const lerpMat2 = (from: Mat2, to: Mat2, t: number): Mat2 =>
  mat2(lerp(from.a, to.a, t), lerp(from.b, to.b, t), lerp(from.c, to.c, t), lerp(from.d, to.d, t));

/**
 * Rotation angle θ of the polar decomposition A = R(θ)·S (S symmetric).
 * From the symmetry condition on R(−θ)·A:  tan θ = (c − b) / (a + d).
 */
export const polarAngle = (m: Mat2): number => Math.atan2(m.c - m.b, m.a + m.d);

/** Shortest signed angular distance from one angle to another. */
const angleDelta = (from: number, to: number): number =>
  Math.atan2(Math.sin(to - from), Math.cos(to - from));

/**
 * Rotation-aware matrix interpolation via polar decomposition:
 * decompose both endpoints as A = R(θ)·S, interpolate θ along the shortest
 * arc and lerp the stretch part S entrywise. Rotations animate as rotations
 * (det never collapses through 0 for orientation-preserving endpoints).
 *
 * Orientation-reversing (det ≤ 0) endpoints have no rotation-only path from
 * the identity, so those fall back to entrywise lerp — the momentary flatten
 * is then the *honest* picture of what a reflection does to the plane.
 */
export function interpolateTransform(from: Mat2, to: Mat2, t: number): Mat2 {
  if (determinant(from) > EPSILON && determinant(to) > EPSILON) {
    const thetaFrom = polarAngle(from);
    const thetaTo = polarAngle(to);
    const stretchFrom = multiply(rotation(-thetaFrom), from);
    const stretchTo = multiply(rotation(-thetaTo), to);
    const theta = thetaFrom + angleDelta(thetaFrom, thetaTo) * t;
    return multiply(rotation(theta), lerpMat2(stretchFrom, stretchTo, t));
  }
  return lerpMat2(from, to, t);
}
