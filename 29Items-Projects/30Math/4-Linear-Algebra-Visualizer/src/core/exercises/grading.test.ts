import { describe, expect, it } from 'vitest';

import { mat2 } from '../math/matrix2';
import { vec2 } from '../math/vector2';
import { grade } from './grading';
import type { Exercise, ExerciseAnswer, ExercisePayload } from './types';

const makeExercise = (payload: ExercisePayload, expected: ExerciseAnswer): Exercise => ({
  id: 'test',
  topic: 'vectors',
  difficulty: 1,
  seed: 0,
  promptTex: '',
  payload,
  expected,
});

describe('grade', () => {
  it('accepts numbers within tolerance and rejects outside it', () => {
    const exercise = makeExercise(
      { kind: 'determinant', matrix: mat2(1, 0, 0, 2) },
      { kind: 'number', value: 2 },
    );
    expect(grade(exercise, { kind: 'number', value: 2 }).correct).toBe(true);
    expect(grade(exercise, { kind: 'number', value: 2.0005 }).correct).toBe(true);
    expect(grade(exercise, { kind: 'number', value: 2.1 }).correct).toBe(false);
  });

  it('grades eigenvalue pairs order-insensitively', () => {
    const exercise = makeExercise(
      { kind: 'eigenvalues', matrix: mat2(2, 1, 1, 2) },
      { kind: 'numberPair', values: [3, 1] },
    );
    expect(grade(exercise, { kind: 'numberPair', values: [1, 3] }).correct).toBe(true);
    expect(grade(exercise, { kind: 'numberPair', values: [3, 1] }).correct).toBe(true);
    expect(grade(exercise, { kind: 'numberPair', values: [3, 2] }).correct).toBe(false);
  });

  it('grades linear-combination coefficients in order (α, β)', () => {
    const exercise = makeExercise(
      { kind: 'linear-combination', basis: [vec2(1, 0), vec2(0, 1)], target: vec2(2, 3) },
      { kind: 'numberPair', values: [2, 3] },
    );
    expect(grade(exercise, { kind: 'numberPair', values: [2, 3] }).correct).toBe(true);
    expect(grade(exercise, { kind: 'numberPair', values: [3, 2] }).correct).toBe(false);
  });

  it('grades vector sums componentwise', () => {
    const exercise = makeExercise(
      { kind: 'vector-sum', u: vec2(1, 1), v: vec2(2, 0) },
      { kind: 'vector', value: vec2(3, 1) },
    );
    expect(grade(exercise, { kind: 'vector', value: vec2(3, 1) }).correct).toBe(true);
    expect(grade(exercise, { kind: 'vector', value: vec2(1, 3) }).correct).toBe(false);
  });

  it('grades eigenvectors by colinearity — any nonzero multiple works', () => {
    const exercise = makeExercise(
      { kind: 'eigenvector', matrix: mat2(2, 1, 1, 2), eigenvalue: 3 },
      { kind: 'vector', value: vec2(1, 1) },
    );
    expect(grade(exercise, { kind: 'vector', value: vec2(1, 1) }).correct).toBe(true);
    expect(grade(exercise, { kind: 'vector', value: vec2(-2, -2) }).correct).toBe(true);
    expect(grade(exercise, { kind: 'vector', value: vec2(0.5, 0.5) }).correct).toBe(true);
    expect(grade(exercise, { kind: 'vector', value: vec2(1, 2) }).correct).toBe(false);
    expect(grade(exercise, { kind: 'vector', value: vec2(0, 0) }).correct).toBe(false);
  });

  it('flags mismatched answer kinds as a bug, never as correct', () => {
    const exercise = makeExercise(
      { kind: 'determinant', matrix: mat2(1, 0, 0, 1) },
      { kind: 'number', value: 1 },
    );
    const result = grade(exercise, { kind: 'vector', value: vec2(1, 1) });
    expect(result.correct).toBe(false);
    expect(result.feedbackTex).toContain('bug');
  });

  it('offers the expected answer in failure feedback', () => {
    const exercise = makeExercise(
      { kind: 'determinant', matrix: mat2(3, 0, 0, 2) },
      { kind: 'number', value: 6 },
    );
    expect(grade(exercise, { kind: 'number', value: 5 }).feedbackTex).toContain('6');
  });
});
