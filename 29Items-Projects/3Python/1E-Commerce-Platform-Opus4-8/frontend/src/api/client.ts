// Typed fetch wrapper. Centralizes base URL, auth header, error-envelope
// parsing, and one-shot access-token refresh on 401.
import type { ApiError } from "@/types";

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "/api/v1";

const ACCESS_KEY = "access_token";
const REFRESH_KEY = "refresh_token";

export const tokenStore = {
  get access() {
    return localStorage.getItem(ACCESS_KEY);
  },
  get refresh() {
    return localStorage.getItem(REFRESH_KEY);
  },
  set(access: string, refresh?: string) {
    localStorage.setItem(ACCESS_KEY, access);
    if (refresh) localStorage.setItem(REFRESH_KEY, refresh);
  },
  clear() {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  },
};

export class ApiRequestError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public details: Record<string, unknown> = {},
  ) {
    super(message);
    this.name = "ApiRequestError";
  }
}

async function refreshAccessToken(): Promise<boolean> {
  const refresh = tokenStore.refresh;
  if (!refresh) return false;
  const resp = await fetch(`${API_BASE}/auth/token/refresh/`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refresh }),
  });
  if (!resp.ok) {
    tokenStore.clear();
    return false;
  }
  const data = (await resp.json()) as { access: string; refresh?: string };
  tokenStore.set(data.access, data.refresh);
  return true;
}

async function doFetch(path: string, init: RequestInit): Promise<Response> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(init.headers as Record<string, string>),
  };
  if (tokenStore.access) headers.Authorization = `Bearer ${tokenStore.access}`;
  return fetch(`${API_BASE}${path}`, { ...init, headers });
}

export async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  let response = await doFetch(path, init);

  // Transparent refresh-and-retry once on expired access token.
  if (response.status === 401 && tokenStore.refresh) {
    const refreshed = await refreshAccessToken();
    if (refreshed) response = await doFetch(path, init);
  }

  if (response.status === 204 || response.status === 205) {
    return undefined as T;
  }

  if (!response.ok) {
    let payload: ApiError | null = null;
    try {
      payload = (await response.json()) as ApiError;
    } catch {
      /* non-JSON error body */
    }
    throw new ApiRequestError(
      response.status,
      payload?.error?.code ?? "UNKNOWN",
      payload?.error?.message ?? response.statusText,
      payload?.error?.details ?? {},
    );
  }

  return (await response.json()) as T;
}
