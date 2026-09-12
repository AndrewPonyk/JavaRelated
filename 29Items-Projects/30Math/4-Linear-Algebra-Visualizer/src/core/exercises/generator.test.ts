import { describe, expect, it } from 'vitest';

import { eigen } from '../math/eigen';
import { apply } from '../math/matrix2';
import { areColinear, scale, vecApproxEquals } from '../math/vector2';
import { generateExercise } from './generator';
import { TOPICS, type Difficulty } from './types';

describe('generateExercise', () => {
  it('is deterministic: same (topic, difficulty, seed) ⇒ identical exercise', () => {
    for (const topic of TOPICS) {
      const first = generateExercise(topic, 3, 12345);
      const second = generateExercise(topic, 3, 12345);
      expect(second).toEqual(first);
    }
  });

  it('different seeds produce different exercises (spot check)', () => {
    const a = generateExercise('eigenvalues', 3, 1);
    const b = generateExercise('eigenvalues', 3, 2);
    expect(a.payload).not.toEqual(b.payload);
  });

  // Property-style test: the generator's embedded answer must agree with the
  // independent eigen solver, across many seeds. This is the "answer-first"
  // pattern paying for itself.
  it('eigen exercises are mathematically consistent for 50 seeds', () => {
    for (let seed = 1; seed <= 50; seed++) {
      for (const difficulty of [1, 3, 5] as Difficulty[]) {
        const exercise = generateExercise('eigenvalues', difficulty, seed);

        if (exercise.payload.kind === 'eigenvalues') {
          if (exercise.expected.kind !== 'numberPair') throw new Error('unexpected answer shape');
          const result = eigen(exercise.payload.matrix);
          expect(result.kind).toBe('realDistinct');
          if (result.kind !== 'realDistinct') return;
          const solved = [result.pairs[0].value, result.pairs[1].value].sort((x, y) => x - y);
          const embedded = [...exercise.expected.values].sort((x, y) => x - y);
          expect(solved[0]).toBeCloseTo(embedded[0], 6);
          expect(solved[1]).toBeCloseTo(embedded[1], 6);
        } else if (exercise.payload.kind === 'eigenvector') {
          if (exercise.expected.kind !== 'vector') throw new Error('unexpected answer shape');
          // A·v must equal λ·v — the definition of an eigenvector.
          const image = apply(exercise.payload.matrix, exercise.expected.value);
          const scaled = scale(exercise.expected.value, exercise.payload.eigenvalue);
          expect(vecApproxEquals(image, scaled, 1e-9)).toBe(true);
          expect(
            areColinear(image, exercise.expected.value) || exercise.payload.eigenvalue === 0,
          ).toBe(true);
        } else {
          throw new Error(`unexpected payload kind ${exercise.payload.kind}`);
        }
      }
    }
  });

  it('mixes in eigenvector questions from difficulty 4', () => {
    const kinds = new Set(
      Array.from({ length: 40 }, (_, i) => generateExercise('eigenvalues', 5, i + 1).payload.kind),
    );
    expect(kinds.has('eigenvector')).toBe(true);
    expect(kinds.has('eigenvalues')).toBe(true);

    for (let seed = 1; seed <= 40; seed++) {
      expect(generateExercise('eigenvalues', 2, seed).payload.kind).toBe('eigenvalues');
    }
  });

  it('embeds correct answers for every non-eigen topic (20 seeds each)', () => {
    for (let seed = 1; seed <= 20; seed++) {
      const sum = generateExercise('vectors', 2, seed);
      if (sum.payload.kind !== 'vector-sum' || sum.expected.kind !== 'vector')
        throw new Error('shape');
      expect(sum.expected.value.x).toBe(sum.payload.u.x + sum.payload.v.x);
      expect(sum.expected.value.y).toBe(sum.payload.u.y + sum.payload.v.y);

      const combo = generateExercise('linear-combinations', 3, seed);
      if (combo.payload.kind !== 'linear-combination' || combo.expected.kind !== 'numberPair')
        throw new Error('shape');
      const [alpha, beta] = combo.expected.values;
      const [u, v] = combo.payload.basis;
      expect(alpha * u.x + beta * v.x).toBeCloseTo(combo.payload.target.x, 9);
      expect(alpha * u.y + beta * v.y).toBeCloseTo(combo.payload.target.y, 9);

      const transform = generateExercise('transformations', 3, seed);
      if (transform.payload.kind !== 'apply-transform' || transform.expected.kind !== 'vector')
        throw new Error('shape');
      const image = apply(transform.payload.matrix, transform.payload.input);
      expect(vecApproxEquals(transform.expected.value, image)).toBe(true);

      const det = generateExercise('determinant', 3, seed);
      if (det.payload.kind !== 'determinant' || det.expected.kind !== 'number')
        throw new Error('shape');
      const m = det.payload.matrix;
      expect(det.expected.value).toBe(m.a * m.d - m.b * m.c);
    }
  });

  it('stamps id, topic, difficulty and seed onto the exercise', () => {
    const exercise = generateExercise('determinant', 2, 99);
    expect(exercise.id).toBe('determinant-d2-s99');
    expect(exercise.topic).toBe('determinant');
    expect(exercise.difficulty).toBe(2);
    expect(exercise.seed).toBe(99);
    expect(exercise.promptTex.length).toBeGreaterThan(0);
  });
});
