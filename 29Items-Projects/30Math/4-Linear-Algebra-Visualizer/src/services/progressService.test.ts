import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  fetchProgress,
  flushPendingAttempts,
  getOrCreateDeviceId,
  pendingAttemptCount,
  recordAttemptQueued,
  type RecordAttemptInput,
} from './progressService';

const mockFetch = vi.fn();
vi.stubGlobal('fetch', mockFetch);

const okJson = (body: unknown) => new Response(JSON.stringify(body), { status: 200 });
const errorJson = (status: number) =>
  new Response(JSON.stringify({ error: { code: 'X', message: 'rejected' } }), { status });

const attempt: RecordAttemptInput = {
  attemptId: '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d',
  deviceId: 'test-device-12345',
  topic: 'vectors',
  difficulty: 1,
  seed: 7,
  correct: true,
  durationMs: 1200,
};

const secondAttempt: RecordAttemptInput = {
  ...attempt,
  attemptId: '1c9e6679-7425-40de-944b-e07fc1f90ae7',
  seed: 8,
};

const confirmedRow = { topic: 'vectors', mastery: 0.15, attempts: 1 };

describe('progressService', () => {
  beforeEach(() => {
    localStorage.clear();
    mockFetch.mockReset();
  });

  it('mints a device id once and reuses it', () => {
    const first = getOrCreateDeviceId();
    const second = getOrCreateDeviceId();
    expect(first).toBe(second);
    expect(first.length).toBeGreaterThanOrEqual(8);
  });

  it('fetchProgress validates the response shape with zod', async () => {
    mockFetch.mockResolvedValueOnce(okJson({ progress: [confirmedRow] }));
    await expect(fetchProgress('test-device-12345')).resolves.toEqual([confirmedRow]);

    mockFetch.mockResolvedValueOnce(okJson({ progress: [{ topic: 'nonsense', mastery: 2 }] }));
    await expect(fetchProgress('test-device-12345')).rejects.toBeDefined();
  });

  it('recordAttemptQueued returns the server-confirmed row on success', async () => {
    mockFetch.mockResolvedValueOnce(okJson({ ok: true, progress: confirmedRow }));
    await expect(recordAttemptQueued(attempt)).resolves.toEqual(confirmedRow);
    expect(pendingAttemptCount()).toBe(0);
  });

  it('parks failed attempts in the retry queue instead of throwing', async () => {
    mockFetch.mockRejectedValueOnce(new Error('offline'));
    await expect(recordAttemptQueued(attempt)).resolves.toBeNull();
    expect(pendingAttemptCount()).toBe(1);
  });

  it('does NOT queue permanently rejected attempts (non-429 4xx)', async () => {
    mockFetch.mockResolvedValueOnce(errorJson(400));
    await expect(recordAttemptQueued(attempt)).resolves.toBeNull();
    expect(pendingAttemptCount()).toBe(0);
  });

  it('queues rate-limited (429) and server-error (5xx) attempts for retry', async () => {
    mockFetch.mockResolvedValueOnce(errorJson(429));
    await recordAttemptQueued(attempt);
    mockFetch.mockResolvedValueOnce(errorJson(503));
    await recordAttemptQueued(secondAttempt);
    expect(pendingAttemptCount()).toBe(2);
  });

  it('flushPendingAttempts drains the queue when the network returns', async () => {
    mockFetch.mockRejectedValueOnce(new Error('offline'));
    await recordAttemptQueued(attempt);
    mockFetch.mockRejectedValueOnce(new Error('still offline'));
    await recordAttemptQueued(secondAttempt);
    expect(pendingAttemptCount()).toBe(2);

    // Fresh Response per call — a Response body can only be consumed once.
    mockFetch.mockImplementation(async () => okJson({ ok: true, progress: confirmedRow }));
    await expect(flushPendingAttempts()).resolves.toBe(2);
    expect(pendingAttemptCount()).toBe(0);
  });

  it('keeps still-failing attempts queued after a partial flush', async () => {
    mockFetch.mockRejectedValueOnce(new Error('offline'));
    await recordAttemptQueued(attempt);
    mockFetch.mockRejectedValueOnce(new Error('offline'));
    await recordAttemptQueued(secondAttempt);

    mockFetch
      .mockResolvedValueOnce(okJson({ ok: true, progress: confirmedRow }))
      .mockRejectedValueOnce(new Error('flaky'));
    await expect(flushPendingAttempts()).resolves.toBe(1);
    expect(pendingAttemptCount()).toBe(1);
  });

  it('drops permanently rejected attempts during flush instead of jamming the queue', async () => {
    mockFetch.mockRejectedValueOnce(new Error('offline'));
    await recordAttemptQueued(attempt);

    mockFetch.mockResolvedValueOnce(errorJson(400));
    await expect(flushPendingAttempts()).resolves.toBe(0);
    expect(pendingAttemptCount()).toBe(0);
  });

  it('is non-reentrant: concurrent flushes share a single pass', async () => {
    mockFetch.mockRejectedValueOnce(new Error('offline'));
    await recordAttemptQueued(attempt);

    let release: (response: Response) => void = () => {};
    mockFetch.mockImplementationOnce(() => new Promise<Response>((resolve) => (release = resolve)));

    const first = flushPendingAttempts();
    const second = flushPendingAttempts();
    expect(second).toBe(first); // literally the same promise

    release(okJson({ ok: true, progress: confirmedRow }));
    await expect(first).resolves.toBe(1);
    // One failed initial POST + exactly one flush POST — no duplicate delivery.
    expect(mockFetch).toHaveBeenCalledTimes(2);
    expect(pendingAttemptCount()).toBe(0);
  });

  it('recovers from a corrupted queue instead of crashing', async () => {
    localStorage.setItem('lav.pendingAttempts', '{not json');
    expect(pendingAttemptCount()).toBe(0);
    await expect(flushPendingAttempts()).resolves.toBe(0);
  });
});
