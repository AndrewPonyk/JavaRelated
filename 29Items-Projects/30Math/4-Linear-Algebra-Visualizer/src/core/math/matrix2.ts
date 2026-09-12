import { type Vec2, vec2, EPSILON } from './vector2';

/**
 * Row-major 2×2 real matrix:
 *
 *     | a  b |
 *     | c  d |
 *
 * Applied to a column vector v = (x, y):  A·v = (a·x + b·y, c·x + d·y).
 * The FIRST column (a, c) is the image of î; the SECOND column (b, d) is the image of ĵ —
 * this is the convention the whole visualizer teaches.
 */
export interface Mat2 {
  readonly a: number;
  readonly b: number;
  readonly c: number;
  readonly d: number;
}

export const mat2 = (a: number, b: number, c: number, d: number): Mat2 => ({ a, b, c, d });

export const IDENTITY: Mat2 = mat2(1, 0, 0, 1);

export const apply = (m: Mat2, v: Vec2): Vec2 => vec2(m.a * v.x + m.b * v.y, m.c * v.x + m.d * v.y);

/** Matrix product m·n (n applied first, then m — standard composition order). */
export const multiply = (m: Mat2, n: Mat2): Mat2 =>
  mat2(m.a * n.a + m.b * n.c, m.a * n.b + m.b * n.d, m.c * n.a + m.d * n.c, m.c * n.b + m.d * n.d);

export const determinant = (m: Mat2): number => m.a * m.d - m.b * m.c;

export const trace = (m: Mat2): number => m.a + m.d;

export const transpose = (m: Mat2): Mat2 => mat2(m.a, m.c, m.b, m.d);

/** Returns `null` for singular matrices — degeneracy is data, not an exception. */
export const inverse = (m: Mat2): Mat2 | null => {
  const det = determinant(m);
  if (Math.abs(det) <= EPSILON) return null;
  const k = 1 / det;
  return mat2(m.d * k, -m.b * k, -m.c * k, m.a * k);
};

export const fromColumns = (c1: Vec2, c2: Vec2): Mat2 => mat2(c1.x, c2.x, c1.y, c2.y);

export const columns = (m: Mat2): [Vec2, Vec2] => [vec2(m.a, m.c), vec2(m.b, m.d)];

/* ── Named transformations (used by UI presets and exercise generators) ── */

/** Counter-clockwise rotation by `theta` radians. */
export const rotation = (theta: number): Mat2 => {
  const c = Math.cos(theta);
  const s = Math.sin(theta);
  return mat2(c, -s, s, c);
};

export const scaling = (sx: number, sy: number = sx): Mat2 => mat2(sx, 0, 0, sy);

/** Horizontal shear: (x, y) ↦ (x + k·y, y). */
export const shear = (k: number): Mat2 => mat2(1, k, 0, 1);

export const matApproxEquals = (m: Mat2, n: Mat2, eps = 1e-6): boolean =>
  Math.abs(m.a - n.a) <= eps &&
  Math.abs(m.b - n.b) <= eps &&
  Math.abs(m.c - n.c) <= eps &&
  Math.abs(m.d - n.d) <= eps;
