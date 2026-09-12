/**
 * Thin fetch wrapper: query-param serialization (repeated params for arrays),
 * RFC-7807 problem-details parsing, session header, AbortSignal pass-through.
 */

import { getSessionId } from "@/utils/session";

const BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? "/api/v1";

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly code: string = "unknown",
    readonly requestId: string = "-",
  ) {
    super(message);
    this.name = "ApiError";
  }
}

type ParamValue = string | number | boolean | string[] | undefined | null;

export function buildQuery(params: Record<string, ParamValue>): string {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === "") continue;
    if (Array.isArray(value)) {
      for (const item of value) query.append(key, item);
    } else {
      query.set(key, String(value));
    }
  }
  const s = query.toString();
  return s ? `?${s}` : "";
}

function baseHeaders(): Record<string, string> {
  return { Accept: "application/json", "X-Session-ID": getSessionId() };
}

async function handle<T>(response: Response): Promise<T> {
  if (!response.ok) {
    // Backend errors are RFC-7807 problem+json; fall back to status text.
    let detail = response.statusText;
    let code = "unknown";
    let requestId = response.headers.get("x-request-id") ?? "-";
    try {
      const problem = await response.json();
      detail = problem.detail ?? detail;
      code = problem.code ?? code;
      requestId = problem.request_id ?? requestId;
    } catch {
      /* non-JSON error body — keep fallbacks */
    }
    throw new ApiError(response.status, detail, code, requestId);
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export async function apiGet<T>(
  path: string,
  params: Record<string, ParamValue> = {},
  signal?: AbortSignal,
): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}${buildQuery(params)}`, {
    signal,
    headers: baseHeaders(),
  });
  return handle<T>(response);
}

export async function apiPost<T>(path: string, body: unknown, signal?: AbortSignal): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
    method: "POST",
    signal,
    headers: { ...baseHeaders(), "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  return handle<T>(response);
}
