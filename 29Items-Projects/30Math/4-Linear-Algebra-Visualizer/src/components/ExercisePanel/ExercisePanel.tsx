import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { MathNotation } from '@/components/MathNotation/MathNotation';
import { chooseNext } from '@/core/exercises/difficulty';
import { generateExercise } from '@/core/exercises/generator';
import { grade } from '@/core/exercises/grading';
import type { Exercise, ExerciseAnswer, TopicProgress } from '@/core/exercises/types';
import {
  fetchProgress,
  flushPendingAttempts,
  getOrCreateDeviceId,
  recordAttemptQueued,
} from '@/services/progressService';
import { useProgressStore } from '@/state/progressStore';
import { useVisualizerStore } from '@/state/visualizerStore';

/**
 * The practice loop: flush queued attempts → fetch progress → pick
 * topic/difficulty → generate exercise → grade locally → persist attempt
 * (fire-and-forget with offline retry queue) → reconcile with the
 * server-confirmed mastery.
 *
 * Async state is an explicit machine (loading | error | ready) with visible
 * Retry / Continue-offline paths — see ARCHITECTURE.md §2.6.
 */

type LoadState =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; exercise: Exercise };

const randomSeed = (): number => Math.floor(Math.random() * 2 ** 31);

function nextExerciseFrom(progress: readonly TopicProgress[]): Exercise {
  const { topic, difficulty } = chooseNext(progress);
  return generateExercise(topic, difficulty, randomSeed());
}

/** Accepts decimals and simple fractions ("-3", "0.5", "1/3"). */
export function parseNumeric(raw: string): number | null {
  const s = raw.trim();
  if (!s) return null;
  const fraction = s.match(/^([+-]?\d+(?:\.\d+)?)\s*\/\s*([+-]?\d+(?:\.\d+)?)$/);
  if (fraction) {
    const denominator = Number(fraction[2]);
    return denominator === 0 ? null : Number(fraction[1]) / denominator;
  }
  const n = Number(s);
  return Number.isFinite(n) ? n : null;
}

function parseAnswer(exercise: Exercise, inputs: [string, string]): ExerciseAnswer | null {
  const first = parseNumeric(inputs[0]);
  const second = parseNumeric(inputs[1]);
  switch (exercise.expected.kind) {
    case 'number':
      return first !== null ? { kind: 'number', value: first } : null;
    case 'numberPair':
      return first !== null && second !== null
        ? { kind: 'numberPair', values: [first, second] }
        : null;
    case 'vector':
      return first !== null && second !== null
        ? { kind: 'vector', value: { x: first, y: second } }
        : null;
  }
}

function inputLabels(exercise: Exercise): [string, string] {
  switch (exercise.payload.kind) {
    case 'vector-sum':
    case 'apply-transform':
    case 'eigenvector':
      return ['x', 'y'];
    case 'determinant':
      return ['det(A)', ''];
    case 'linear-combination':
      return ['α', 'β'];
    case 'eigenvalues':
      return ['λ₁', 'λ₂'];
  }
}

const TOPIC_LABELS: Record<TopicProgress['topic'], string> = {
  vectors: 'Vectors',
  'linear-combinations': 'Linear combinations',
  transformations: 'Transformations',
  determinant: 'Determinant',
  eigenvalues: 'Eigenvalues',
};

/** Server-side zod cap on durationMs — clamp instead of getting a permanent 400. */
const MAX_DURATION_MS = 3_600_000;

export function ExercisePanel() {
  const [state, setState] = useState<LoadState>({ status: 'loading' });
  const [inputs, setInputs] = useState<[string, string]>(['', '']);
  const [feedbackTex, setFeedbackTex] = useState<string | null>(null);
  /** One graded submission per exercise — no answer-farming after feedback. */
  const [graded, setGraded] = useState(false);
  const startedAt = useRef<number>(performance.now());
  const deviceId = useMemo(getOrCreateDeviceId, []);

  const setMatrix = useVisualizerStore((s) => s.setMatrix);
  const progress = useProgressStore((s) => s.progress);
  const streak = useProgressStore((s) => s.streak);
  const applyAttempt = useProgressStore((s) => s.applyAttempt);
  const reconcile = useProgressStore((s) => s.reconcile);
  const setFromServer = useProgressStore((s) => s.setFromServer);

  const load = useCallback(async () => {
    setState({ status: 'loading' });
    try {
      // Queue first: the fetch that follows then reflects the flushed attempts.
      await flushPendingAttempts();
      const remote = await fetchProgress(deviceId);
      setFromServer(remote);
      setState({
        status: 'ready',
        exercise: nextExerciseFrom(useProgressStore.getState().progress),
      });
    } catch (error) {
      setState({
        status: 'error',
        message: error instanceof Error ? error.message : 'Failed to load progress',
      });
    }
  }, [deviceId, setFromServer]);

  useEffect(() => {
    void load();
  }, [load]);

  const exercise = state.status === 'ready' ? state.exercise : null;

  // New exercise ⇒ reset inputs/feedback/timer and mirror its matrix in the canvas.
  useEffect(() => {
    if (!exercise) return;
    setInputs(['', '']);
    setFeedbackTex(null);
    setGraded(false);
    startedAt.current = performance.now();
    if ('matrix' in exercise.payload) {
      setMatrix(exercise.payload.matrix);
    }
  }, [exercise, setMatrix]);

  /** Offline mode uses the locally cached (persisted) progress. */
  const continueOffline = () => {
    setState({
      status: 'ready',
      exercise: nextExerciseFrom(useProgressStore.getState().progress),
    });
  };

  const submit = () => {
    if (state.status !== 'ready' || graded) return;
    const answer = parseAnswer(state.exercise, inputs);
    if (!answer) {
      setFeedbackTex('\\text{Please enter valid number(s) — decimals and fractions work.}');
      return;
    }

    const result = grade(state.exercise, answer);
    setFeedbackTex(result.feedbackTex);
    setGraded(true);
    applyAttempt(state.exercise.topic, result.correct);

    // Fire-and-forget with retry queue; server-confirmed mastery wins when it
    // lands. The attemptId makes retries idempotent server-side.
    void recordAttemptQueued({
      attemptId: crypto.randomUUID(),
      deviceId,
      topic: state.exercise.topic,
      difficulty: state.exercise.difficulty,
      seed: state.exercise.seed,
      correct: result.correct,
      durationMs: Math.min(Math.round(performance.now() - startedAt.current), MAX_DURATION_MS),
    }).then((confirmed) => {
      if (confirmed) reconcile(confirmed);
    });
  };

  const next = () => {
    if (state.status !== 'ready') return;
    setState({
      status: 'ready',
      exercise: nextExerciseFrom(useProgressStore.getState().progress),
    });
  };

  if (state.status === 'loading') {
    return (
      <section className="panel" aria-busy="true">
        <h2>Practice</h2>
        <p data-testid="exercise-loading">Loading your progress…</p>
      </section>
    );
  }

  if (state.status === 'error') {
    return (
      <section className="panel">
        <h2>Practice</h2>
        <p role="alert">Could not load progress: {state.message}</p>
        <div className="button-row">
          <button type="button" onClick={() => void load()}>
            Retry
          </button>
          <button type="button" onClick={continueOffline}>
            Continue offline
          </button>
        </div>
      </section>
    );
  }

  const { exercise: current } = state;
  const mastery = progress.find((p) => p.topic === current.topic)?.mastery ?? 0;
  const labels = inputLabels(current);
  const needsSecondInput = current.expected.kind !== 'number';

  return (
    <section className="panel exercise-panel">
      <h2>Practice</h2>
      <p className="exercise-meta">
        Topic: <strong>{TOPIC_LABELS[current.topic]}</strong> · Difficulty {current.difficulty}/5 ·
        Mastery {(mastery * 100).toFixed(0)}%{streak > 1 && <> · 🔥 {streak} streak</>}
      </p>

      <p className="exercise-prompt">
        <MathNotation block tex={current.promptTex} />
      </p>

      <div className="answer-inputs">
        <label>
          {labels[0]}
          <input
            inputMode="decimal"
            value={inputs[0]}
            onChange={(e) => setInputs([e.target.value, inputs[1]])}
            onKeyDown={(e) => {
              if (e.key === 'Enter') submit();
            }}
          />
        </label>
        {needsSecondInput && (
          <label>
            {labels[1]}
            <input
              inputMode="decimal"
              value={inputs[1]}
              onChange={(e) => setInputs([inputs[0], e.target.value])}
              onKeyDown={(e) => {
                if (e.key === 'Enter') submit();
              }}
            />
          </label>
        )}
      </div>

      <div className="button-row">
        <button type="button" onClick={submit} disabled={graded}>
          Submit
        </button>
        <button type="button" onClick={next}>
          Next exercise
        </button>
      </div>

      {feedbackTex && (
        <p className="exercise-feedback" role="status">
          <MathNotation tex={feedbackTex} />
        </p>
      )}

      <div className="mastery-list" aria-label="Mastery per topic">
        {progress.map((p) => (
          <div key={p.topic} className="mastery-row">
            <span className="mastery-label">{TOPIC_LABELS[p.topic]}</span>
            <div
              className="mastery-bar"
              role="progressbar"
              aria-valuenow={Math.round(p.mastery * 100)}
              aria-valuemin={0}
              aria-valuemax={100}
              aria-label={`${TOPIC_LABELS[p.topic]} mastery`}
            >
              <div className="mastery-fill" style={{ width: `${p.mastery * 100}%` }} />
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
