/**
 * HTTP helpers for the backend.
 *
 * Centralizes base URLs, auth header injection, JSON parsing, and error
 * normalization so components/hooks stay declarative. Tokens are held in memory
 * only — never localStorage (PHI/XSS risk).
 */
import type { OperationOutcome } from '@/types/fhir';

const FHIR_BASE = import.meta.env.VITE_FHIR_BASE_URL ?? '/fhir';
const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '/api/v1';

let accessToken: string | null = null;

/** Set by the auth flow after login (SMART-on-FHIR). */
export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

function authHeaders(extra?: HeadersInit): HeadersInit {
  return {
    ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
    ...extra,
  };
}

// --- FHIR (/fhir) ------------------------------------------------------------

export async function fhirGet<T>(path: string): Promise<T> {
  const response = await fetch(`${FHIR_BASE}${path}`, {
    headers: authHeaders({ Accept: 'application/fhir+json' }),
  });
  if (!response.ok) {
    throw new ApiError(response.status, await fhirError(response));
  }
  return (await response.json()) as T;
}

async function fhirError(response: Response): Promise<string> {
  try {
    const outcome = (await response.json()) as OperationOutcome;
    return outcome.issue?.[0]?.diagnostics ?? `Request failed (${response.status})`;
  } catch {
    return `Request failed (${response.status})`;
  }
}

// --- REST JSON (/api/v1) -----------------------------------------------------

export async function apiGet<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: authHeaders({ Accept: 'application/json' }),
  });
  if (!response.ok) {
    throw new ApiError(response.status, await jsonError(response));
  }
  return (await response.json()) as T;
}

export async function apiPost<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json', Accept: 'application/json' }),
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    throw new ApiError(response.status, await jsonError(response));
  }
  return (await response.json()) as T;
}

async function jsonError(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as { message?: string };
    return body.message ?? `Request failed (${response.status})`;
  } catch {
    return `Request failed (${response.status})`;
  }
}
