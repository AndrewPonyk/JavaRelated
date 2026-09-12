import type { ZodType } from 'zod';

import { env } from '@/app/env';
import { logger } from '@/lib/logger';

/**
 * Every failure the transport layer produces — HTTP errors, timeouts, and contract
 * violations (zod parse failures) — surfaces as an ApiError. Features branch on
 * `status`/`code`, never on message strings (docs/ARCHITECTURE.md §2.6).
 */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly details?: unknown,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

/**
 * In-memory access-token store. Deliberately NOT localStorage/sessionStorage: an XSS
 * payload cannot lift what never persists. The refresh token lives in an httpOnly
 * SameSite=Strict cookie owned by the API (docs/ARCHITECTURE.md §2.5).
 */
export const tokenStore = {
  accessToken: null as string | null,
  set(token: string | null): void {
    this.accessToken = token;
  },
  clear(): void {
    this.accessToken = null;
  },
};

interface RequestOptions<T> {
  method?: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE';
  body?: unknown;
  /** When provided, the response is parsed — contract drift fails fast, not as undefined UI. */
  schema?: ZodType<T>;
  signal?: AbortSignal;
  /** Attach the bearer token (default true). */
  auth?: boolean;
  timeoutMs?: number;
}

function anySignal(signals: AbortSignal[]): AbortSignal {
  const controller = new AbortController();
  for (const signal of signals) {
    if (signal.aborted) {
      controller.abort(signal.reason);
      break;
    }
    signal.addEventListener('abort', () => controller.abort(signal.reason), { once: true });
  }
  return controller.signal;
}

/**
 * Silent refresh (docs/ARCHITECTURE.md §2.5): exchanges the httpOnly refresh cookie for a
 * new access token. Single-flight — concurrent 401s share one refresh round-trip instead
 * of stampeding the endpoint.
 */
let refreshInFlight: Promise<boolean> | null = null;

async function tryRefreshSession(): Promise<boolean> {
  refreshInFlight ??= (async () => {
    try {
      const response = await fetch(`${env.apiBaseUrl}/v1/auth/refresh`, {
        method: 'POST',
        headers: { Accept: 'application/json' },
        credentials: 'include', // the refresh cookie is the credential
        signal: AbortSignal.timeout(10_000),
      });
      if (!response.ok) return false;
      const json = (await response.json()) as { accessToken?: unknown };
      if (typeof json.accessToken !== 'string' || json.accessToken.length === 0) {
        logger.error('Refresh endpoint returned an invalid payload');
        return false;
      }
      tokenStore.set(json.accessToken);
      return true;
    } catch (error) {
      logger.warn('Session refresh failed', { error: String(error) });
      return false;
    }
  })();

  try {
    return await refreshInFlight;
  } finally {
    refreshInFlight = null;
  }
}

async function request<T>(
  path: string,
  options: RequestOptions<T> = {},
  isReplay = false,
): Promise<T> {
  const { method = 'GET', body, schema, signal, auth = true, timeoutMs = 15_000 } = options;

  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth && tokenStore.accessToken) headers.Authorization = `Bearer ${tokenStore.accessToken}`;

  const timeout = AbortSignal.timeout(timeoutMs);
  const composedSignal = signal ? anySignal([signal, timeout]) : timeout;

  let response: Response;
  try {
    response = await fetch(`${env.apiBaseUrl}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: composedSignal,
      credentials: 'include', // refresh cookie travels only to the API origin
    });
  } catch (cause) {
    if (signal?.aborted) throw cause; // caller-initiated abort is not an error condition
    if (timeout.aborted) {
      throw new ApiError(0, 'TIMEOUT', `Request to ${path} timed out after ${timeoutMs}ms`);
    }
    throw new ApiError(0, 'NETWORK_ERROR', `Network request to ${path} failed`, cause);
  }

  if (response.status === 401 && auth && !isReplay) {
    // Expired access token? Attempt ONE silent refresh and replay the original request.
    if (await tryRefreshSession()) {
      return request<T>(path, options, true);
    }
    tokenStore.clear(); // refresh denied → session is truly over
  }

  if (!response.ok) {
    const problem = (await response.json().catch(() => null)) as {
      code?: string;
      message?: string;
      details?: unknown;
    } | null;
    throw new ApiError(
      response.status,
      problem?.code ?? 'HTTP_ERROR',
      problem?.message ?? `Request failed with status ${response.status}`,
      problem?.details,
    );
  }

  if (response.status === 204) return undefined as T;

  const json: unknown = await response.json();
  if (schema) {
    const parsed = schema.safeParse(json);
    if (!parsed.success) {
      logger.error('API contract violation', { path, issues: parsed.error.issues });
      throw new ApiError(
        response.status,
        'CONTRACT_VIOLATION',
        `Unexpected response shape from ${path}`,
        parsed.error.issues,
      );
    }
    return parsed.data;
  }
  return json as T;
}

export const api = {
  get: <T>(path: string, options?: Omit<RequestOptions<T>, 'method' | 'body'>) =>
    request<T>(path, options),
  post: <T>(path: string, body?: unknown, options?: Omit<RequestOptions<T>, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'POST', body }),
  patch: <T>(path: string, body: unknown, options?: Omit<RequestOptions<T>, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'PATCH', body }),
  put: <T>(path: string, body: unknown, options?: Omit<RequestOptions<T>, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'PUT', body }),
  delete: <T>(path: string, options?: Omit<RequestOptions<T>, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'DELETE' }),
};
