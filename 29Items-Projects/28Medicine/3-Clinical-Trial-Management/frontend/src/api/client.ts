// Typed API client with an auth interceptor and a consistent error envelope.
// All requests go through here so auth, correlation ids and error shaping live
// in one place (ARCHITECTURE §2.2 / §2.6).

const BASE_URL = "/api/v1";

export class ApiError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly status: number,
    public readonly correlationId?: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

// TODO: replace with a real token store (refresh rotation) wired to the IdP.
function getAccessToken(): string | null {
  return sessionStorage.getItem("access_token");
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = getAccessToken();
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (init.body) headers.set("Content-Type", "application/json");
  if (token) headers.set("Authorization", `Bearer ${token}`);
  // Correlation id stitches SPA → API → worker traces together.
  headers.set("X-Correlation-Id", crypto.randomUUID());

  const res = await fetch(`${BASE_URL}${path}`, { ...init, headers });

  if (!res.ok) {
    const correlationId = res.headers.get("X-Correlation-Id") ?? undefined;
    let code = "HTTP_ERROR";
    let message = res.statusText;
    try {
      const body = await res.json();
      code = body?.error?.code ?? code;
      message = body?.error?.message ?? message;
    } catch {
      /* non-JSON error body — keep defaults */
    }
    throw new ApiError(code, message, res.status, correlationId);
  }
  return (res.status === 204 ? undefined : await res.json()) as T;
}

// Build a RequestInit, only including `body` when present (exactOptionalPropertyTypes
// disallows assigning `undefined` to an optional property explicitly).
function withBody(method: string, body?: unknown): RequestInit {
  const init: RequestInit = { method };
  if (body !== undefined) init.body = JSON.stringify(body);
  return init;
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) => request<T>(path, withBody("POST", body)),
  patch: <T>(path: string, body: unknown) => request<T>(path, withBody("PATCH", body)),
  delete: <T>(path: string) => request<T>(path, withBody("DELETE")),
};
