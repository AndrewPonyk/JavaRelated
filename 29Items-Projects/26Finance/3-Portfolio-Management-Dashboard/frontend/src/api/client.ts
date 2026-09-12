// Typed fetch wrapper for the backend API.
// Centralizes base URL, auth header, and error-envelope unwrapping so
// components never touch `fetch` directly.

import type {
  Asset,
  AttributionResponse,
  BacktestResponse,
  FrontierResponse,
  Holding,
  JobStatus,
  OptimizedPortfolio,
  Portfolio,
  RiskMetrics,
  User,
} from "../types/portfolio";

const BASE = import.meta.env.VITE_API_BASE_URL ?? "";
const TOKEN_KEY = "access_token";

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

function authHeaders(): Record<string, string> {
  const token = tokenStore.get();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const res = await fetch(`${BASE}/api/v1${path}`, {
    ...options,
    headers: { "Content-Type": "application/json", ...authHeaders(), ...(options.headers ?? {}) },
  });

  if (!res.ok) {
    let message = res.statusText;
    try {
      const body = await res.json();
      message = body?.error?.message ?? body?.detail ?? message;
    } catch {
      /* non-JSON body */
    }
    throw new ApiError(res.status, message);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export const api = {
  // --- Auth ---
  register: (email: string, password: string, fullName?: string) =>
    request<User>("/auth/register", {
      method: "POST",
      body: JSON.stringify({ email, password, full_name: fullName }),
    }),

  async login(email: string, password: string): Promise<string> {
    // OAuth2 password flow expects form-encoded data.
    const form = new URLSearchParams({ username: email, password });
    const res = await fetch(`${BASE}/api/v1/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: form.toString(),
    });
    if (!res.ok) throw new ApiError(res.status, "Incorrect email or password");
    const data = await res.json();
    tokenStore.set(data.access_token);
    return data.access_token;
  },

  me: () => request<User>("/auth/me"),

  // --- Assets ---
  listAssets: () => request<Asset[]>("/assets"),
  createAsset: (payload: { symbol: string; name: string; asset_class?: string; currency?: string }) =>
    request<Asset>("/assets", { method: "POST", body: JSON.stringify(payload) }),
  seedPrices: (assetId: number, days = 504) =>
    request<{ bars_inserted: number }>(`/assets/${assetId}/seed-prices?days=${days}`, {
      method: "POST",
    }),

  // --- Portfolios ---
  listPortfolios: () => request<Portfolio[]>("/portfolios"),
  getPortfolio: (id: number) => request<Portfolio>(`/portfolios/${id}`),
  createPortfolio: (payload: {
    name: string;
    description?: string;
    holdings: { asset_id: number; quantity: number; cost_basis?: number }[];
  }) => request<Portfolio>("/portfolios", { method: "POST", body: JSON.stringify(payload) }),
  updatePortfolio: (id: number, payload: { name?: string; description?: string }) =>
    request<Portfolio>(`/portfolios/${id}`, { method: "PATCH", body: JSON.stringify(payload) }),
  deletePortfolio: (id: number) => request<void>(`/portfolios/${id}`, { method: "DELETE" }),

  // --- Holdings ---
  addHolding: (portfolioId: number, payload: { asset_id: number; quantity: number; cost_basis?: number }) =>
    request<Holding>(`/portfolios/${portfolioId}/holdings`, {
      method: "POST",
      body: JSON.stringify(payload),
    }),
  updateHolding: (portfolioId: number, holdingId: number, payload: { quantity?: number; cost_basis?: number }) =>
    request<Holding>(`/portfolios/${portfolioId}/holdings/${holdingId}`, {
      method: "PATCH",
      body: JSON.stringify(payload),
    }),
  deleteHolding: (portfolioId: number, holdingId: number) =>
    request<void>(`/portfolios/${portfolioId}/holdings/${holdingId}`, { method: "DELETE" }),

  // --- Analytics ---
  optimize: (portfolioId: number, body: Record<string, unknown> = {}) =>
    request<OptimizedPortfolio>(`/portfolios/${portfolioId}/optimize`, {
      method: "POST",
      body: JSON.stringify(body),
    }),
  getFrontier: (portfolioId: number, body: Record<string, unknown> = { n_points: 50 }) =>
    request<FrontierResponse>(`/portfolios/${portfolioId}/frontier`, {
      method: "POST",
      body: JSON.stringify(body),
    }),
  getRisk: (portfolioId: number, lookbackDays = 252) =>
    request<RiskMetrics>(`/portfolios/${portfolioId}/risk?lookback_days=${lookbackDays}`),
  getAttribution: (portfolioId: number, lookbackDays = 252) =>
    request<AttributionResponse>(`/portfolios/${portfolioId}/attribution?lookback_days=${lookbackDays}`),
  runBacktest: (portfolioId: number, body: Record<string, unknown> = {}) =>
    request<BacktestResponse>(`/portfolios/${portfolioId}/backtest`, {
      method: "POST",
      body: JSON.stringify(body),
    }),

  // --- Monte Carlo (async job) ---
  dispatchMonteCarlo: (portfolioId: number, body: Record<string, unknown> = {}) =>
    request<{ job_id: string; status: string }>(`/portfolios/${portfolioId}/monte-carlo`, {
      method: "POST",
      body: JSON.stringify(body),
    }),
  getJob: (jobId: string) => request<JobStatus>(`/jobs/${jobId}`),
};
