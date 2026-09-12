/**
 * Sliding-window rate limiter, in-memory per function instance.
 *
 * BEST EFFORT by design: serverless instances don't share memory, so a burst
 * spread across instances sees N× the budget. That still stops the common
 * abuse case (one client hammering one warm instance); platform-level
 * protection (Vercel WAF / a Redis-backed limiter) is the distributed answer
 * and is documented in ARCHITECTURE.md §2.5.
 */

const WINDOW_MS = 60_000;
const MAX_REQUESTS_PER_WINDOW = 30;
const MAX_TRACKED_KEYS = 10_000;

const hits = new Map<string, number[]>();

/** Returns true when the request is allowed; false ⇒ respond 429. */
export function checkRateLimit(key: string, now: number = Date.now()): boolean {
  const windowStart = now - WINDOW_MS;
  const recent = (hits.get(key) ?? []).filter((t) => t > windowStart);

  if (recent.length >= MAX_REQUESTS_PER_WINDOW) {
    hits.set(key, recent);
    return false;
  }

  recent.push(now);
  // Crude memory bound: reset tracking rather than grow unbounded.
  if (!hits.has(key) && hits.size >= MAX_TRACKED_KEYS) hits.clear();
  hits.set(key, recent);
  return true;
}

/** Test hook: clears all tracked windows. */
export function resetRateLimiter(): void {
  hits.clear();
}
