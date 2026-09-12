// @vitest-environment node
import { Prisma } from '@prisma/client';
import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * Repository tests against a mocked Prisma client — they verify the
 * transaction flow, the server-side mastery computation, idempotent replays
 * and the enum mapping without needing a database. Full end-to-end
 * persistence runs against the docker-compose Postgres (see README).
 */

const tx = {
  user: { upsert: vi.fn() },
  exerciseAttempt: { create: vi.fn(), findUnique: vi.fn() },
  topicProgress: { findUnique: vi.fn(), upsert: vi.fn() },
};

const prismaMock = {
  $transaction: vi.fn(async (fn: (t: typeof tx) => Promise<unknown>) => fn(tx)),
  user: { findUnique: vi.fn() },
};

vi.mock('../../../api/_lib/db', () => ({
  get prisma() {
    return prismaMock;
  },
}));

import { getProgress, recordAttempt } from '../../../api/_lib/progressRepo';

const input = {
  attemptId: '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d',
  deviceId: 'device-123456',
  topic: 'linear-combinations' as const,
  difficulty: 2,
  seed: 9,
  correct: true,
  durationMs: 3000,
};

describe('progressRepo.recordAttempt', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    prismaMock.$transaction.mockImplementation(async (fn: (t: typeof tx) => Promise<unknown>) =>
      fn(tx),
    );
    tx.user.upsert.mockResolvedValue({ id: 'user-1', deviceId: 'device-123456' });
    tx.exerciseAttempt.findUnique.mockResolvedValue(null); // no replay by default
    tx.exerciseAttempt.create.mockResolvedValue({});
  });

  it('computes first-attempt mastery server-side (0 → 0.15 on correct)', async () => {
    tx.topicProgress.findUnique.mockResolvedValueOnce(null);
    tx.topicProgress.upsert.mockImplementationOnce(
      async (args: { update: { mastery: number } }) => ({
        mastery: args.update.mastery,
        attemptsCount: 1,
      }),
    );

    const row = await recordAttempt(input);

    expect(row).toEqual({ topic: 'linear-combinations', mastery: 0.15, attempts: 1 });
    const upsertArgs = tx.topicProgress.upsert.mock.calls[0][0] as {
      update: { mastery: number };
      create: { topic: string };
    };
    expect(upsertArgs.update.mastery).toBeCloseTo(0.15, 9);
    expect(upsertArgs.create.topic).toBe('LINEAR_COMBINATIONS'); // slug → DB enum
  });

  it('applies the loss rule to existing mastery on a wrong answer', async () => {
    tx.topicProgress.findUnique.mockResolvedValueOnce({ mastery: 0.5, attemptsCount: 4 });
    tx.topicProgress.upsert.mockResolvedValueOnce({ mastery: 0.35, attemptsCount: 5 });

    const row = await recordAttempt({ ...input, correct: false });

    const upsertArgs = tx.topicProgress.upsert.mock.calls[0][0] as {
      update: { mastery: number };
    };
    expect(upsertArgs.update.mastery).toBeCloseTo(0.35, 9);
    expect(row.attempts).toBe(5);
  });

  it('stores the attempt row with the mapped enum topic and the idempotency key', async () => {
    tx.topicProgress.findUnique.mockResolvedValueOnce(null);
    tx.topicProgress.upsert.mockResolvedValueOnce({ mastery: 0.15, attemptsCount: 1 });

    await recordAttempt(input);

    const createArgs = tx.exerciseAttempt.create.mock.calls[0][0] as {
      data: { topic: string; seed: number; correct: boolean; clientAttemptId: string };
    };
    expect(createArgs.data.topic).toBe('LINEAR_COMBINATIONS');
    expect(createArgs.data.seed).toBe(9);
    expect(createArgs.data.correct).toBe(true);
    expect(createArgs.data.clientAttemptId).toBe(input.attemptId);
  });

  it('treats a replayed attemptId as a no-op and returns the current row', async () => {
    tx.exerciseAttempt.findUnique.mockResolvedValueOnce({ id: 'attempt-1' });
    tx.topicProgress.findUnique.mockResolvedValueOnce({ mastery: 0.15, attemptsCount: 1 });

    const row = await recordAttempt(input);

    expect(row).toEqual({ topic: 'linear-combinations', mastery: 0.15, attempts: 1 });
    expect(tx.exerciseAttempt.create).not.toHaveBeenCalled();
    expect(tx.topicProgress.upsert).not.toHaveBeenCalled();
  });

  it('resolves a unique-constraint race as a replay instead of a 500', async () => {
    prismaMock.$transaction.mockRejectedValueOnce(
      new Prisma.PrismaClientKnownRequestError('Unique constraint failed', {
        code: 'P2002',
        clientVersion: 'test',
      }),
    );
    prismaMock.user.findUnique.mockResolvedValueOnce({
      id: 'user-1',
      progress: [{ topic: 'LINEAR_COMBINATIONS', mastery: 0.15, attemptsCount: 1 }],
    });

    const row = await recordAttempt(input);
    expect(row).toEqual({ topic: 'linear-combinations', mastery: 0.15, attempts: 1 });
  });

  it('rethrows non-constraint transaction failures', async () => {
    prismaMock.$transaction.mockRejectedValueOnce(new Error('connection lost'));
    await expect(recordAttempt(input)).rejects.toThrow('connection lost');
  });
});

describe('progressRepo.getProgress', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('returns [] for unknown devices', async () => {
    prismaMock.user.findUnique.mockResolvedValueOnce(null);
    await expect(getProgress('device-000000')).resolves.toEqual([]);
  });

  it('maps DB enum topics back to slugs', async () => {
    prismaMock.user.findUnique.mockResolvedValueOnce({
      id: 'user-1',
      progress: [
        { topic: 'EIGENVALUES', mastery: 0.7, attemptsCount: 11 },
        { topic: 'VECTORS', mastery: 0.9, attemptsCount: 20 },
      ],
    });

    await expect(getProgress('device-123456')).resolves.toEqual([
      { topic: 'eigenvalues', mastery: 0.7, attempts: 11 },
      { topic: 'vectors', mastery: 0.9, attempts: 20 },
    ]);
  });
});
