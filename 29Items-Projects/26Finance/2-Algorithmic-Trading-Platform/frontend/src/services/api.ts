// Typed API client for the trading platform backend.
// Centralizes base URL, auth header injection, and error normalization so
// components/hooks never touch fetch() directly.

import type {
  CreateStrategyRequest,
  Order,
  PnLSummary,
  Position,
  Strategy,
} from '../types/strategy';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8000/api/v1';

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let resp: Response;
  try {
    resp = await fetch(`${BASE_URL}${path}`, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        // TODO: inject `Authorization: Bearer <token>` from the OIDC session.
        ...init?.headers,
      },
    });
  } catch (cause) {
    // Network/transport failure (server down, CORS, offline).
    throw new ApiError(0, `Network error: ${(cause as Error).message}`);
  }

  if (!resp.ok) {
    const detail = await resp.json().catch(() => ({}));
    throw new ApiError(resp.status, detail?.detail ?? resp.statusText);
  }
  if (resp.status === 204) return undefined as T;
  return (await resp.json()) as T;
}

export const strategiesApi = {
  list: () => request<Strategy[]>('/strategies'),
  get: (id: string) => request<Strategy>(`/strategies/${id}`),
  create: (body: CreateStrategyRequest) =>
    request<Strategy>('/strategies', { method: 'POST', body: JSON.stringify(body) }),
  halt: (id: string) =>
    request<Strategy>(`/strategies/${id}/halt`, { method: 'POST' }),
  remove: (id: string) =>
    request<void>(`/strategies/${id}`, { method: 'DELETE' }),
};

export const portfolioApi = {
  positions: () => request<Position[]>('/positions'),
  orders: (limit = 100) => request<Order[]>(`/orders?limit=${limit}`),
  pnl: () => request<PnLSummary>('/pnl'),
};
