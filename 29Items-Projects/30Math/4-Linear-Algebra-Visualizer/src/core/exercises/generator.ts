import { formatNumber, matrixToTex, vectorToTex } from '../format/tex';
import { apply, determinant, fromColumns, mat2, multiply, type Mat2 } from '../math/matrix2';
import { add, scale, vec2, type Vec2 } from '../math/vector2';
import { createRng, type Rng } from './rng';
import type { Difficulty, Exercise, Topic } from './types';

/**
 * Exercise generators — one per topic, all following the ANSWER-FIRST pattern:
 * pick the (nice) answer, then derive the question from it. This guarantees
 * clean numbers at low difficulty (integer eigenvalues, integer coefficients)
 * without ever solving the problem "forwards" with floats.
 */

type ExerciseDraft = Omit<Exercise, 'id' | 'topic' | 'difficulty' | 'seed'>;
type GeneratorFn = (rng: Rng, difficulty: Difficulty) => ExerciseDraft;

/** Entry magnitude grows with difficulty. */
const magnitude = (difficulty: Difficulty): number => 1 + difficulty;

const randomVector = (rng: Rng, r: number): Vec2 => vec2(rng.int(-r, r), rng.int(-r, r));

const randomMatrix = (rng: Rng, r: number): Mat2 =>
  mat2(rng.int(-r, r), rng.int(-r, r), rng.int(-r, r), rng.int(-r, r));

const generateVectorSum: GeneratorFn = (rng, difficulty) => {
  const r = magnitude(difficulty);
  const u = randomVector(rng, r);
  const v = randomVector(rng, r);
  return {
    promptTex:
      `\\vec{u} = ${vectorToTex(u)},\\; \\vec{v} = ${vectorToTex(v)}.` +
      ` \\;\\text{Compute } \\vec{u} + \\vec{v}.`,
    payload: { kind: 'vector-sum', u, v },
    expected: { kind: 'vector', value: add(u, v) },
  };
};

const generateLinearCombination: GeneratorFn = (rng, difficulty) => {
  // Unimodular basis (det = 1) keeps the unique solution integral.
  const p = rng.int(-2, 2);
  const q = rng.int(-2, 2);
  const u = vec2(1, q);
  const v = vec2(p, 1 + p * q);
  const alpha = rng.intNonZero(-magnitude(difficulty), magnitude(difficulty));
  const beta = rng.intNonZero(-magnitude(difficulty), magnitude(difficulty));
  const target = add(scale(u, alpha), scale(v, beta));
  return {
    promptTex:
      `\\text{Find } \\alpha, \\beta \\text{ such that } ` +
      `${vectorToTex(target)} = \\alpha ${vectorToTex(u)} + \\beta ${vectorToTex(v)}.`,
    payload: { kind: 'linear-combination', basis: [u, v], target },
    expected: { kind: 'numberPair', values: [alpha, beta] },
  };
};

const generateApplyTransform: GeneratorFn = (rng, difficulty) => {
  const r = magnitude(difficulty);
  const matrix = randomMatrix(rng, Math.min(r, 3));
  const input = randomVector(rng, r);
  return {
    promptTex: `A = ${matrixToTex(matrix)},\\; \\vec{v} = ${vectorToTex(input)}. \\;\\text{Compute } A\\vec{v}.`,
    payload: { kind: 'apply-transform', matrix, input },
    expected: { kind: 'vector', value: apply(matrix, input) },
  };
};

const generateDeterminant: GeneratorFn = (rng, difficulty) => {
  const matrix = randomMatrix(rng, Math.min(magnitude(difficulty), 4));
  return {
    promptTex: `A = ${matrixToTex(matrix)}. \\;\\text{Compute } \\det(A).`,
    payload: { kind: 'determinant', matrix },
    expected: { kind: 'number', value: determinant(matrix) },
  };
};

/**
 * Answer-first eigen construction shared by both eigen question variants:
 * pick distinct integer eigenvalues, conjugate D = diag(λ₁, λ₂) by a
 * unimodular P so A = P·D·P⁻¹ has integer entries and exactly those
 * eigenvalues. The columns of P are the eigenvectors.
 */
function buildEigenSystem(rng: Rng, difficulty: Difficulty) {
  const bound = difficulty + 1;
  const lambda1 = rng.intNonZero(-bound, bound);
  let lambda2 = rng.int(-bound, bound);
  while (lambda2 === lambda1) lambda2 = rng.int(-bound, bound);

  const p = rng.int(-difficulty, difficulty);
  const q = rng.int(-difficulty, difficulty);
  const P = fromColumns(vec2(1, q), vec2(p, 1 + p * q)); // det(P) = 1
  const PInverse = mat2(1 + p * q, -p, -q, 1); // adjugate; exact since det = 1
  const A = multiply(multiply(P, mat2(lambda1, 0, 0, lambda2)), PInverse);

  return { A, lambda1, lambda2, eigenvector1: vec2(1, q) };
}

const generateEigenvalues: GeneratorFn = (rng, difficulty) => {
  const { A, lambda1, lambda2, eigenvector1 } = buildEigenSystem(rng, difficulty);

  // From difficulty 4 the topic mixes in "find an eigenvector" questions.
  const askVector = difficulty >= 4 && rng.pick(['values', 'vector'] as const) === 'vector';

  if (askVector) {
    return {
      promptTex:
        `A = ${matrixToTex(A)}. \\;\\lambda = ${formatNumber(lambda1)}` +
        ` \\text{ is an eigenvalue of } A.` +
        ` \\;\\text{Find an eigenvector for } \\lambda.`,
      payload: { kind: 'eigenvector', matrix: A, eigenvalue: lambda1 },
      expected: { kind: 'vector', value: eigenvector1 },
    };
  }

  return {
    promptTex: `A = ${matrixToTex(A)}. \\;\\text{Find the eigenvalues of } A.`,
    payload: { kind: 'eigenvalues', matrix: A },
    expected: { kind: 'numberPair', values: [lambda1, lambda2] },
  };
};

const generators: Record<Topic, GeneratorFn> = {
  vectors: generateVectorSum,
  'linear-combinations': generateLinearCombination,
  transformations: generateApplyTransform,
  determinant: generateDeterminant,
  eigenvalues: generateEigenvalues,
};

export function generateExercise(topic: Topic, difficulty: Difficulty, seed: number): Exercise {
  const rng = createRng(seed);
  const draft = generators[topic](rng, difficulty);
  return {
    id: `${topic}-d${difficulty}-s${seed}`,
    topic,
    difficulty,
    seed,
    ...draft,
  };
}
