import { z } from 'zod';

import { TOPICS, type TopicProgress } from '@/core/exercises/types';
import { ApiError, request } from './apiClient';

/**
 * Progress persistence over /api. Every response is zod-validated — the client
 * trusts the wire exactly as much as the server does: not at all.
 *
 * Attempts are recorded fire-and-forget with an OFFLINE RETRY QUEUE: a
 * retryable failure lands in localStorage and is flushed on the next app boot,
 * so persistence failures never interrupt learning (ARCHITECTURE.md §2.6).
 * Every attempt carries a client-generated `attemptId`, making delivery
 * effectively exactly-once: the server treats replays as no-ops.
 */

const TopicProgressSchema = z.object({
  topic: z.enum(TOPICS),
  mastery: z.number().min(0).max(1),
  attempts: z.number().int().nonnegative(),
});

const ProgressResponseSchema = z.object({
  progress: z.array(TopicProgressSchema),
});

const AttemptResponseSchema = z.object({
  ok: z.literal(true),
  progress: TopicProgressSchema,
});

const PendingAttemptSchema = z.object({
  attemptId: z.string().uuid(),
  deviceId: z.string().min(8).max(64),
  topic: z.enum(TOPICS),
  difficulty: z.number().int().min(1).max(5),
  seed: z.number().int().nonnegative(),
  correct: z.boolean(),
  durationMs: z.number().int().nonnegative(),
});

export type RecordAttemptInput = z.infer<typeof PendingAttemptSchema>;

const DEVICE_ID_STORAGE_KEY = 'lav.deviceId';
const PENDING_QUEUE_STORAGE_KEY = 'lav.pendingAttempts';
const PENDING_QUEUE_LIMIT = 100;

/** Anonymous-first identity: a random UUID pinned in localStorage (ARCHITECTURE §2.5). */
export function getOrCreateDeviceId(): string {
  const existing = localStorage.getItem(DEVICE_ID_STORAGE_KEY);
  if (existing) return existing;
  const created = crypto.randomUUID();
  localStorage.setItem(DEVICE_ID_STORAGE_KEY, created);
  return created;
}

export async function fetchProgress(deviceId: string): Promise<TopicProgress[]> {
  const raw = await request<unknown>(`/progress?deviceId=${encodeURIComponent(deviceId)}`);
  return ProgressResponseSchema.parse(raw).progress;
}

/** Throwing variant — used by the queue flusher and `recordAttemptQueued`. */
export async function recordAttempt(input: RecordAttemptInput): Promise<TopicProgress> {
  const raw = await request<unknown>('/attempts', {
    method: 'POST',
    body: JSON.stringify(input),
  });
  return AttemptResponseSchema.parse(raw).progress;
}

/**
 * A failure is worth retrying when the request might succeed later: network
 * errors, timeouts, 5xx, 429. A non-429 4xx is permanently invalid — retrying
 * it would jam the queue forever.
 */
const isRetryable = (error: unknown): boolean => {
  if (error instanceof ApiError) return error.status === 429 || error.status >= 500;
  return true;
};

/**
 * Never throws: returns the server-confirmed progress row, or `null` after
 * either parking the attempt in the retry queue (retryable failure) or
 * dropping it (permanently rejected).
 */
export async function recordAttemptQueued(
  input: RecordAttemptInput,
): Promise<TopicProgress | null> {
  try {
    return await recordAttempt(input);
  } catch (error) {
    if (isRetryable(error)) enqueuePendingAttempt(input);
    return null;
  }
}

let flushInFlight: Promise<number> | null = null;

/**
 * POSTs queued attempts; retryable failures stay queued, permanent rejections
 * are dropped. Returns how many got through. Non-reentrant: concurrent callers
 * (React StrictMode double-mount, Retry button) share one flush.
 */
export function flushPendingAttempts(): Promise<number> {
  flushInFlight ??= doFlush().finally(() => {
    flushInFlight = null;
  });
  return flushInFlight;
}

async function doFlush(): Promise<number> {
  const pending = readQueue();
  if (pending.length === 0) return 0;

  const remaining: RecordAttemptInput[] = [];
  let flushed = 0;
  for (const attempt of pending) {
    try {
      await recordAttempt(attempt);
      flushed += 1;
    } catch (error) {
      if (isRetryable(error)) remaining.push(attempt);
      // else: permanently invalid — dropping it is the only way to unjam the queue.
    }
  }
  writeQueue(remaining);
  return flushed;
}

export function pendingAttemptCount(): number {
  return readQueue().length;
}

function enqueuePendingAttempt(input: RecordAttemptInput): void {
  const queue = readQueue();
  queue.push(input);
  // Bounded queue: drop the oldest attempts rather than growing forever.
  writeQueue(queue.slice(-PENDING_QUEUE_LIMIT));
}

function readQueue(): RecordAttemptInput[] {
  try {
    const raw = localStorage.getItem(PENDING_QUEUE_STORAGE_KEY);
    if (!raw) return [];
    return z.array(PendingAttemptSchema).parse(JSON.parse(raw));
  } catch {
    // Corrupted cache is not worth crashing over — start fresh.
    localStorage.removeItem(PENDING_QUEUE_STORAGE_KEY);
    return [];
  }
}

function writeQueue(queue: RecordAttemptInput[]): void {
  if (queue.length === 0) {
    localStorage.removeItem(PENDING_QUEUE_STORAGE_KEY);
    return;
  }
  localStorage.setItem(PENDING_QUEUE_STORAGE_KEY, JSON.stringify(queue));
}
