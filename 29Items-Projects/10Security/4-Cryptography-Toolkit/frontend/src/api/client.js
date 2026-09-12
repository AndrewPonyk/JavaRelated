/**
 * Fetch wrapper normalizing every failure into one thrown ApiError so all
 * components share identical {loading, error, data} handling
 * (ARCHITECTURE §2.6). Demo inputs stay ephemeral — never localStorage.
 * The auth TOKEN lives in sessionStorage (cleared with the tab) — demo
 * plaintexts/keys are never persisted anywhere.
 */

const BASE = import.meta.env.VITE_API_BASE_URL ?? "/api";
const TOKEN_KEY = "ctk_token";

export class ApiError extends Error {
  constructor(message, { code, status, correlationId } = {}) {
    super(message);
    this.name = "ApiError";
    this.code = code ?? "unknown";
    this.status = status ?? 0;
    this.correlationId = correlationId;
  }
}

export const auth = {
  getToken() {
    try {
      return sessionStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  setToken(token) {
    try {
      token
        ? sessionStorage.setItem(TOKEN_KEY, token)
        : sessionStorage.removeItem(TOKEN_KEY);
    } catch {
      /* storage unavailable (private mode) — auth just won't persist */
    }
  },
  authHeaders() {
    const token = this.getToken();
    return token ? { Authorization: `Bearer ${token}` } : {};
  },
};

export async function apiFetch(
  path,
  { method = "GET", body, auth: needsAuth = false } = {},
) {
  let res;
  try {
    res = await fetch(`${BASE}${path}`, {
      method,
      headers: {
        ...(body ? { "Content-Type": "application/json" } : {}),
        ...(needsAuth ? auth.authHeaders() : {}),
      },
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError("Network error — is the backend running?", {
      code: "network_error",
    });
  }

  // /api/openapi.json and /api/docs intentionally return raw bodies
  const json = await res.json().catch(() => null);
  if (!res.ok) {
    const e = json?.error ?? {};
    throw new ApiError(e.message ?? `Request failed (${res.status})`, {
      code: e.code,
      status: res.status,
      correlationId: e.correlation_id,
    });
  }
  if (json && "data" in json) return json.data;
  return json;
}
