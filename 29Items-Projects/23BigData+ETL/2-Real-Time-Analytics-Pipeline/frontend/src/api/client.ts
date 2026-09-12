import type {
  AggregatePoint,
  AnomalyAlert,
  CreateMetricRequest,
  MetricDefinition,
  WindowSize,
} from '../types/metrics';

// Same-origin by default (Vite dev proxy / CloudFront routing); override via VITE_API_BASE_URL.
export const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '';

/** Error carrying the RFC 7807 problem-details body the API returns. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly title: string,
    readonly detail?: string,
    /** Field-level validation errors ({field: message}) when the API provides them. */
    readonly errors?: Record<string, string>,
  ) {
    super(detail ? `${title}: ${detail}` : title);
    this.name = 'ApiError';
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  });
  if (!res.ok) {
    let title = `HTTP ${res.status}`;
    let detail: string | undefined;
    let errors: Record<string, string> | undefined;
    try {
      const problem = await res.json();
      title = problem.title ?? title;
      detail = problem.detail;
      errors = problem.errors;
    } catch {
      /* non-JSON error body — keep the status text */
    }
    throw new ApiError(res.status, title, detail, errors);
  }
  return res.json() as Promise<T>;
}

export function getMetricDefinitions(signal?: AbortSignal): Promise<MetricDefinition[]> {
  return request('/api/v1/metrics', { signal });
}

export function createMetricDefinition(body: CreateMetricRequest): Promise<MetricDefinition> {
  return request('/api/v1/metrics', { method: 'POST', body: JSON.stringify(body) });
}

export function getAggregates(
  metricKey: string,
  window: WindowSize,
  signal?: AbortSignal,
): Promise<AggregatePoint[]> {
  const params = new URLSearchParams({ window });
  return request(`/api/v1/metrics/${encodeURIComponent(metricKey)}/aggregates?${params}`, {
    signal,
  });
}

export function getAlerts(
  status: 'open' | 'acknowledged' | 'resolved' | 'all',
  signal?: AbortSignal,
): Promise<AnomalyAlert[]> {
  return request(`/api/v1/alerts?status=${status}`, { signal });
}

export function acknowledgeAlert(alertId: string): Promise<AnomalyAlert> {
  return request(`/api/v1/alerts/${encodeURIComponent(alertId)}/ack`, { method: 'POST' });
}
