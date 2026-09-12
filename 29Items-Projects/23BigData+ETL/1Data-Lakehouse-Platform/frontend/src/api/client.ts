// Single fetch boundary for the Console. If client-side state grows beyond
// these hooks, swap the hook layer for TanStack Query — this module stays.

const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8000";

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

function authHeaders(): Record<string, string> {
  // In enforced-auth deployments the SSO callback stores the bearer token here;
  // in dev mode the API accepts requests without one.
  const token = typeof localStorage !== "undefined" ? localStorage.getItem("lakehouse_token") : null;
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export async function fetchJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: { "Content-Type": "application/json", ...authHeaders(), ...init?.headers },
  });

  if (!response.ok) {
    let detail = response.statusText;
    try {
      const body = (await response.json()) as { detail?: string; title?: string };
      detail = body.detail ?? body.title ?? detail;
    } catch {
      // non-JSON error body — keep statusText
    }
    throw new ApiError(response.status, detail);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export const api = {
  get: <T>(path: string, init?: RequestInit) => fetchJson<T>(path, init),
  post: <T>(path: string, body: unknown) =>
    fetchJson<T>(path, { method: "POST", body: JSON.stringify(body) }),
  patch: <T>(path: string, body: unknown) =>
    fetchJson<T>(path, { method: "PATCH", body: JSON.stringify(body) }),
  delete: (path: string) => fetchJson<undefined>(path, { method: "DELETE" }),
};
