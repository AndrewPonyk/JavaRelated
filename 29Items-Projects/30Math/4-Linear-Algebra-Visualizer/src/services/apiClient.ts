/**
 * Minimal typed fetch wrapper — the ONLY place the frontend talks HTTP.
 * Base URL, JSON headers, timeout and error normalization live here once.
 */

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api';
const DEFAULT_TIMEOUT_MS = 10_000;

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
    signal: AbortSignal.timeout(DEFAULT_TIMEOUT_MS),
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(init.headers as Record<string, string> | undefined),
    },
  });

  if (!response.ok) {
    const body = await response.text().catch(() => '');
    // Server errors arrive as { error: { code, message } } — surface the message when present.
    let message = body || `Request failed with status ${response.status}`;
    try {
      const parsed = JSON.parse(body) as { error?: { message?: string } };
      if (parsed.error?.message) message = parsed.error.message;
    } catch {
      /* non-JSON error body — keep raw text */
    }
    throw new ApiError(response.status, message);
  }

  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}
