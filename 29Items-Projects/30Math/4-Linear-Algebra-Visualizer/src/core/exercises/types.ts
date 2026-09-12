import type { Mat2 } from '../math/matrix2';
import type { Vec2 } from '../math/vector2';

/** Curriculum order matters: progression unlocks topics front-to-back. */
export const TOPICS = [
  'vectors',
  'linear-combinations',
  'transformations',
  'determinant',
  'eigenvalues',
] as const;

export type Topic = (typeof TOPICS)[number];

export type Difficulty = 1 | 2 | 3 | 4 | 5;

/** What the learner is asked about. */
export type ExercisePayload =
  | { kind: 'vector-sum'; u: Vec2; v: Vec2 }
  | { kind: 'linear-combination'; basis: readonly [Vec2, Vec2]; target: Vec2 }
  | { kind: 'apply-transform'; matrix: Mat2; input: Vec2 }
  | { kind: 'determinant'; matrix: Mat2 }
  | { kind: 'eigenvalues'; matrix: Mat2 }
  /** "λ is an eigenvalue of A — find an eigenvector." Graded by colinearity. */
  | { kind: 'eigenvector'; matrix: Mat2; eigenvalue: number };

export type ExerciseAnswer =
  | { kind: 'vector'; value: Vec2 }
  | { kind: 'number'; value: number }
  /** Ordered for linear combinations (α, β); unordered for eigenvalues. */
  | { kind: 'numberPair'; values: readonly [number, number] };

export interface Exercise {
  /** `${topic}-d${difficulty}-s${seed}` — reproducible from its parts. */
  readonly id: string;
  readonly topic: Topic;
  readonly difficulty: Difficulty;
  readonly seed: number;
  /** LaTeX prompt, rendered by MathJax. */
  readonly promptTex: string;
  readonly payload: ExercisePayload;
  /**
   * Expected answer. Exercises are generated and graded client-side by design
   * (self-study tool); mastery, however, is recomputed server-side from the
   * correctness bit — see ARCHITECTURE.md §2.5.
   */
  readonly expected: ExerciseAnswer;
}

export interface TopicProgress {
  readonly topic: Topic;
  /** 0 (novice) … 1 (mastered). */
  readonly mastery: number;
  readonly attempts: number;
}

export interface GradingResult {
  readonly correct: boolean;
  /** LaTeX feedback shown to the learner. */
  readonly feedbackTex: string;
}
