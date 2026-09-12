import type { EigenResult } from '../math/eigen';
import { determinant, type Mat2 } from '../math/matrix2';
import type { Vec2 } from '../math/vector2';

/** Domain values → LaTeX strings (rendered by MathJax in the presentation layer). */

/** Human-friendly number: rounds to `digits`, trims trailing zeros, kills "-0". */
export const formatNumber = (n: number, digits = 3): string => {
  const rounded = Number(n.toFixed(digits));
  return String(Object.is(rounded, -0) ? 0 : rounded);
};

export const vectorToTex = (v: Vec2): string =>
  `\\begin{pmatrix} ${formatNumber(v.x)} \\\\ ${formatNumber(v.y)} \\end{pmatrix}`;

/** Compact row form for readouts where a column matrix is too tall. */
export const vectorToTexInline = (v: Vec2, digits = 2): string =>
  `(${formatNumber(v.x, digits)},\\; ${formatNumber(v.y, digits)})`;

export const matrixToTex = (m: Mat2): string =>
  `\\begin{pmatrix} ${formatNumber(m.a)} & ${formatNumber(m.b)} \\\\ ` +
  `${formatNumber(m.c)} & ${formatNumber(m.d)} \\end{pmatrix}`;

/** Determinant with its geometric reading: area factor + orientation. */
export const determinantToTex = (m: Mat2): string => {
  const det = determinant(m);
  const area = `\\text{area} \\times ${formatNumber(Math.abs(det), 2)}`;
  const orientation = det < 0 ? ',\\; \\text{orientation flipped}' : '';
  return `\\det(A) = ${formatNumber(det, 2)} \\;\\; (${area}${orientation})`;
};

export const eigenToTex = (result: EigenResult): string => {
  switch (result.kind) {
    case 'realDistinct': {
      const [p1, p2] = result.pairs;
      return (
        `\\lambda_1 = ${formatNumber(p1.value, 2)} \\text{ along } ${vectorToTexInline(p1.vector)},` +
        `\\quad \\lambda_2 = ${formatNumber(p2.value, 2)} \\text{ along } ${vectorToTexInline(p2.vector)}`
      );
    }
    case 'realRepeated':
      return result.geometricMultiplicity === 2
        ? `\\lambda = ${formatNumber(result.value, 2)} \\;\\text{(every direction is invariant)}`
        : `\\lambda = ${formatNumber(result.value, 2)} \\;\\text{(repeated)} \\text{ along } ${vectorToTexInline(result.vector)}`;
    case 'complex': {
      const modulus = Math.hypot(result.real, result.imaginary);
      const argDegrees = (Math.atan2(result.imaginary, result.real) * 180) / Math.PI;
      return (
        `\\lambda = ${formatNumber(result.real, 2)} \\pm ${formatNumber(result.imaginary, 2)}i` +
        ` \\;\\text{(rotation by } ${formatNumber(argDegrees, 1)}^\\circ` +
        `,\\; \\text{scale } ${formatNumber(modulus, 2)}\\text{)}`
      );
    }
  }
};
