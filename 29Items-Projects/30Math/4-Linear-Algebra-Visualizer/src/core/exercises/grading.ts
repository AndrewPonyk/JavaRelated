import { formatNumber, vectorToTex, vectorToTexInline } from '../format/tex';
import { areColinear, vecApproxEquals } from '../math/vector2';
import type { Exercise, ExerciseAnswer, GradingResult } from './types';

/**
 * Grading is tolerant by design: users type decimals ("0.33" for 1/3) and
 * floats are floats. Never compare computed numbers exactly. Eigenvectors are
 * compared by COLINEARITY — (−1,−1) is the same eigenvector as (1,1)
 * (TECH-NOTES §3.6 pitfall 8).
 */
const TOLERANCE = 1e-3;

const close = (a: number, b: number, tol = TOLERANCE): boolean => Math.abs(a - b) <= tol;

const pairMatches = (
  expected: readonly [number, number],
  given: readonly [number, number],
  ordered: boolean,
): boolean => {
  const inOrder = close(expected[0], given[0]) && close(expected[1], given[1]);
  if (ordered) return inOrder;
  return inOrder || (close(expected[0], given[1]) && close(expected[1], given[0]));
};

const expectedToTex = (expected: ExerciseAnswer): string => {
  switch (expected.kind) {
    case 'number':
      return formatNumber(expected.value);
    case 'numberPair':
      return `${formatNumber(expected.values[0])},\\; ${formatNumber(expected.values[1])}`;
    case 'vector':
      return vectorToTex(expected.value);
  }
};

export function grade(exercise: Exercise, given: ExerciseAnswer): GradingResult {
  const { expected, payload } = exercise;

  if (expected.kind === 'vector' && given.kind === 'vector') {
    if (payload.kind === 'eigenvector') {
      const correct = areColinear(expected.value, given.value, TOLERANCE);
      return {
        correct,
        feedbackTex: correct
          ? '\\text{Correct! ✓ (any nonzero multiple works)}'
          : `\\text{Not quite — e.g. } ${vectorToTexInline(expected.value)}` +
            ` \\text{ (or any nonzero multiple).}`,
      };
    }
    const correct = vecApproxEquals(expected.value, given.value, TOLERANCE);
    return buildResult(correct, expected);
  }

  if (expected.kind === 'number' && given.kind === 'number') {
    return buildResult(close(expected.value, given.value), expected);
  }

  if (expected.kind === 'numberPair' && given.kind === 'numberPair') {
    const ordered = payload.kind === 'linear-combination';
    return buildResult(pairMatches(expected.values, given.values, ordered), expected);
  }

  return { correct: false, feedbackTex: '\\text{Answer format mismatch — this is a bug.}' };
}

function buildResult(correct: boolean, expected: ExerciseAnswer): GradingResult {
  return {
    correct,
    feedbackTex: correct
      ? '\\text{Correct! ✓}'
      : `\\text{Not quite — the answer is } ${expectedToTex(expected)}.`,
  };
}
