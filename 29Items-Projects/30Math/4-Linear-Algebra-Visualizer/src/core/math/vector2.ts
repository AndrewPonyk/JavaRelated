/**
 * Immutable 2-D vector algebra. Pure functions only — no rendering, no React.
 * Every operation returns a new object; inputs are never mutated.
 */

export interface Vec2 {
  readonly x: number;
  readonly y: number;
}

/** Tolerance for internal float comparisons. User-facing grading uses a looser one. */
export const EPSILON = 1e-9;

export const vec2 = (x: number, y: number): Vec2 => ({ x, y });

export const ZERO: Vec2 = vec2(0, 0);

export const add = (u: Vec2, v: Vec2): Vec2 => vec2(u.x + v.x, u.y + v.y);

export const sub = (u: Vec2, v: Vec2): Vec2 => vec2(u.x - v.x, u.y - v.y);

export const scale = (v: Vec2, k: number): Vec2 => vec2(v.x * k, v.y * k);

export const dot = (u: Vec2, v: Vec2): number => u.x * v.x + u.y * v.y;

/**
 * 2-D cross product (the z-component of the 3-D cross).
 * Sign encodes orientation; |value| is the parallelogram area spanned by u, v.
 */
export const cross = (u: Vec2, v: Vec2): number => u.x * v.y - u.y * v.x;

export const lengthOf = (v: Vec2): number => Math.hypot(v.x, v.y);

export const isZero = (v: Vec2, eps: number = EPSILON): boolean => lengthOf(v) <= eps;

/** @throws if `v` is (numerically) the zero vector — that is a caller bug, not user input. */
export const normalize = (v: Vec2): Vec2 => {
  const len = lengthOf(v);
  if (len <= EPSILON) {
    throw new Error('Cannot normalize a zero vector');
  }
  return scale(v, 1 / len);
};

export const vecApproxEquals = (u: Vec2, v: Vec2, eps = 1e-6): boolean =>
  Math.abs(u.x - v.x) <= eps && Math.abs(u.y - v.y) <= eps;

/**
 * True when u and v span the same line (either may point the opposite way).
 * This is how eigenvectors must be compared: (−1,−1) ≡ (1,1).
 */
export const areColinear = (u: Vec2, v: Vec2, eps = 1e-6): boolean =>
  !isZero(u, eps) && !isZero(v, eps) && Math.abs(cross(normalize(u), normalize(v))) <= eps;
