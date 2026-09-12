/**
 * Typed fetch wrapper. Understands the backend's problem+json error contract,
 * applies a default timeout, attaches the JWT, and transparently refreshes
 * once on 401 (single-flight so concurrent requests share one refresh).
 */

import type { Problem } from "../types/api";
import { clearTokens, getAccessToken, getRefreshToken, setTokens, type TokenPair } from "./tokens";

// Empty in dev (Vite proxies /api to the backend) and for same-origin deploys;
// set per environment in CI for CloudFront-to-ALB setups.
const BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? "";

const DEFAULT_TIMEOUT_MS = 20_000;

export class ApiError extends Error {
  readonly status: number;
  readonly problem: Problem | null;

  constructor(status: number, problem: Problem | null, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.problem = problem;
  }
}

export interface ApiResult<T> {
  status: number;
  data: T;
}

interface RequestOptions {
  auth?: boolean; // attach Authorization + auto-refresh on 401 (default true)
  timeoutMs?: number;
}

let refreshInFlight: Promise<boolean> | null = null;

async function refreshSession(): Promise<boolean> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return false;
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        const response = await fetch(`${BASE_URL}/api/v1/auth/refresh`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ refresh_token: refreshToken }),
          signal: AbortSignal.timeout(DEFAULT_TIMEOUT_MS),
        });
        if (!response.ok) {
          clearTokens(); // rotation rejected the token — session is over
          return false;
        }
        setTokens((await response.json()) as TokenPair);
        return true;
      } catch {
        return false;
      } finally {
        refreshInFlight = null;
      }
    })();
  }
  return refreshInFlight;
}

async function rawRequest(
  path: string,
  init: RequestInit,
  options: RequestOptions,
): Promise<Response> {
  const headers = new Headers(init.headers);
  if (!headers.has("Content-Type") && init.body !== undefined) {
    headers.set("Content-Type", "application/json");
  }
  const token = getAccessToken();
  if (options.auth !== false && token) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  return fetch(`${BASE_URL}${path}`, {
    ...init,
    headers,
    signal: init.signal ?? AbortSignal.timeout(options.timeoutMs ?? DEFAULT_TIMEOUT_MS),
  });
}

async function requestWithRefresh(
  path: string,
  init: RequestInit,
  options: RequestOptions,
): Promise<Response> {
  let response = await rawRequest(path, init, options);
  if (response.status === 401 && options.auth !== false && getRefreshToken()) {
    const refreshed = await refreshSession();
    if (refreshed) {
      response = await rawRequest(path, init, options);
    }
  }
  return response;
}

async function throwProblem(response: Response): Promise<never> {
  let problem: Problem | null = null;
  try {
    problem = (await response.json()) as Problem;
  } catch {
    /* non-JSON error body (proxy/LB) — fall through to statusText */
  }
  const detail =
    problem?.detail ??
    (problem as unknown as { detail?: string } | null)?.detail ??
    response.statusText;
  throw new ApiError(response.status, problem, detail || `HTTP ${response.status}`);
}

/** JSON request; resolves with status + parsed body (use for 200/202 splits). */
export async function apiRequest<T>(
  path: string,
  init: RequestInit = {},
  options: RequestOptions = {},
): Promise<ApiResult<T>> {
  const response = await requestWithRefresh(path, init, options);
  if (!response.ok) await throwProblem(response);
  if (response.status === 204) {
    return { status: response.status, data: undefined as T };
  }
  return { status: response.status, data: (await response.json()) as T };
}

/** JSON request; resolves with the body only (the common case). */
export async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
  options: RequestOptions = {},
): Promise<T> {
  return (await apiRequest<T>(path, init, options)).data;
}

/** Binary request (plot SVGs, artifacts). */
export async function apiFetchBlob(
  path: string,
  init: RequestInit = {},
  options: RequestOptions = {},
): Promise<Blob> {
  const response = await requestWithRefresh(path, init, options);
  if (!response.ok) await throwProblem(response);
  return response.blob();
}
