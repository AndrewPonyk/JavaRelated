/**
 * Minimal typed fetch wrapper. Centralises base URL, JSON handling, auth header
 * injection and error normalisation so feature code stays declarative.
 *
 * TODO: refresh-token handling on 401; consider TanStack Query for caching,
 *       retries and request de-duplication as the app grows.
 */
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api';

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly type?: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

function authHeader(): Record<string, string> {
  const token = localStorage.getItem('access_token');
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export async function apiGet<T>(path: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
    headers: { Accept: 'application/json', ...authHeader() },
    signal: signal ?? null,
  });

  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as { error?: { message?: string; type?: string } } | null;
    throw new ApiError(response.status, body?.error?.message ?? response.statusText, body?.error?.type);
  }

  return (await response.json()) as T;
}

async function apiSend<T>(method: 'POST' | 'PATCH' | 'PUT', path: string, payload: unknown): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
    method,
    headers: { 'Content-Type': 'application/json', Accept: 'application/json', ...authHeader() },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as { error?: { message?: string; type?: string } } | null;
    throw new ApiError(response.status, body?.error?.message ?? response.statusText, body?.error?.type);
  }

  return (await response.json()) as T;
}

export function apiPost<T>(path: string, payload: unknown): Promise<T> {
  return apiSend<T>('POST', path, payload);
}

export function apiPatch<T>(path: string, payload: unknown): Promise<T> {
  return apiSend<T>('PATCH', path, payload);
}
