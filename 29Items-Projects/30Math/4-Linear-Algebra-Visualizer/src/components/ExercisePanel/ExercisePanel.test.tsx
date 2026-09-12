import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { TopicProgress } from '@/core/exercises/types';
import { useProgressStore } from '@/state/progressStore';
import { ExercisePanel, parseNumeric } from './ExercisePanel';

// Mock the HTTP boundary only — core (generator/grading) always runs for real.
vi.mock('@/services/progressService', () => ({
  getOrCreateDeviceId: vi.fn(() => 'test-device-id'),
  fetchProgress: vi.fn(),
  recordAttemptQueued: vi.fn(() => Promise.resolve(null)),
  flushPendingAttempts: vi.fn(() => Promise.resolve(0)),
}));

import { fetchProgress, recordAttemptQueued } from '@/services/progressService';

const mockedFetchProgress = vi.mocked(fetchProgress);
const mockedRecordAttempt = vi.mocked(recordAttemptQueued);

const someProgress: TopicProgress[] = [{ topic: 'vectors', mastery: 0, attempts: 0 }];

describe('parseNumeric', () => {
  it('accepts integers, decimals and fractions', () => {
    expect(parseNumeric('3')).toBe(3);
    expect(parseNumeric('-0.5')).toBe(-0.5);
    expect(parseNumeric('1/3')).toBeCloseTo(1 / 3, 12);
    expect(parseNumeric('-2/4')).toBeCloseTo(-0.5, 12);
  });

  it('rejects garbage, empties and division by zero', () => {
    expect(parseNumeric('')).toBeNull();
    expect(parseNumeric('abc')).toBeNull();
    expect(parseNumeric('1/0')).toBeNull();
  });
});

describe('<ExercisePanel />', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    useProgressStore.getState().resetAll();
  });

  it('shows a loading state, then a ready exercise', async () => {
    mockedFetchProgress.mockResolvedValueOnce(someProgress);

    render(<ExercisePanel />);
    expect(screen.getByTestId('exercise-loading')).toBeInTheDocument();

    // Curriculum starts at 'vectors' with mastery 0 → difficulty 1.
    expect(await screen.findByRole('button', { name: /submit/i })).toBeInTheDocument();
    expect(screen.getByText(/topic:/i)).toBeInTheDocument();
    expect(screen.getByText(/difficulty 1\/5/i)).toBeInTheDocument();
    expect(screen.getAllByRole('progressbar').length).toBeGreaterThan(0);
  });

  it('shows an error state with Retry and Continue-offline on fetch failure', async () => {
    mockedFetchProgress.mockRejectedValueOnce(new Error('network down'));

    render(<ExercisePanel />);

    expect(await screen.findByRole('alert')).toHaveTextContent(/network down/i);
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /continue offline/i })).toBeInTheDocument();
  });

  it('recovers via Continue offline and rejects an empty answer inline', async () => {
    mockedFetchProgress.mockRejectedValueOnce(new Error('network down'));
    const user = userEvent.setup();

    render(<ExercisePanel />);
    await user.click(await screen.findByRole('button', { name: /continue offline/i }));

    await user.click(screen.getByRole('button', { name: /submit/i }));
    expect(await screen.findByRole('status')).toHaveTextContent(/valid number/i);
    expect(mockedRecordAttempt).not.toHaveBeenCalled();
  });

  it('grades a real answer, updates mastery and records the attempt', async () => {
    mockedFetchProgress.mockResolvedValueOnce(someProgress);
    const user = userEvent.setup();

    render(<ExercisePanel />);
    await screen.findByRole('button', { name: /submit/i });

    // Whatever the exercise is, "999999" is wrong — grading still runs end-to-end.
    const inputs = screen
      .getAllByRole('textbox')
      .filter((el) => el.closest('.answer-inputs') !== null);
    for (const input of inputs) {
      await user.type(input, '999999');
    }
    await user.click(screen.getByRole('button', { name: /submit/i }));

    expect(await screen.findByRole('status')).toHaveTextContent(/not quite/i);
    expect(mockedRecordAttempt).toHaveBeenCalledWith(
      expect.objectContaining({
        attemptId: expect.stringMatching(/^[0-9a-f-]{36}$/i),
        deviceId: 'test-device-id',
        topic: 'vectors',
        difficulty: 1,
        correct: false,
      }),
    );
    // Wrong answer ⇒ streak stays 0, attempt counted.
    const vectors = useProgressStore.getState().progress.find((p) => p.topic === 'vectors');
    expect(vectors?.attempts).toBe(1);

    // One graded submission per exercise: Submit is disabled until Next.
    expect(screen.getByRole('button', { name: /submit/i })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: /next exercise/i }));
    expect(screen.getByRole('button', { name: /submit/i })).toBeEnabled();
    expect(mockedRecordAttempt).toHaveBeenCalledTimes(1);
  });
});
