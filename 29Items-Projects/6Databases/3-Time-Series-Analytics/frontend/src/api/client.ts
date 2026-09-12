/** Typed fetch wrapper + auth storage for the backend API. */

import type { Anomaly, Device, DeviceWithKey, MetricSeries, TokenResponse } from "../types";

const BASE = import.meta.env.VITE_API_BASE_URL ?? "/api/v1";

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export const authStore = {
  get token(): string | null {
    return localStorage.getItem("tsa_token");
  },
  get roles(): string[] {
    try {
      return JSON.parse(localStorage.getItem("tsa_roles") ?? "[]") as string[];
    } catch {
      return [];
    }
  },
  save(token: string, roles: string[]): void {
    localStorage.setItem("tsa_token", token);
    localStorage.setItem("tsa_roles", JSON.stringify(roles));
  },
  clear(): void {
    localStorage.removeItem("tsa_token");
    localStorage.removeItem("tsa_roles");
  },
};

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const token = authStore.token;
  const response = await fetch(`${BASE}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init?.headers,
    },
  });

  if (!response.ok) {
    let message = response.statusText;
    try {
      const body = await response.json();
      message = body?.error?.message ?? body?.detail ?? message;
    } catch {
      /* non-JSON error body — keep statusText */
    }
    throw new ApiError(response.status, message);
  }
  return response.status === 204 ? (undefined as T) : response.json();
}

function rangeQuery(hours: number): string {
  const end = new Date();
  const start = new Date(end.getTime() - hours * 3_600_000);
  return new URLSearchParams({
    start: start.toISOString(),
    end: end.toISOString(),
  }).toString();
}

export const api = {
  login: (username: string, password: string) =>
    request<TokenResponse>("/auth/token", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    }),

  listDevices: () => request<Device[]>("/devices"),

  registerDevice: (payload: { name: string; site: string; device_type: string }) =>
    request<DeviceWithKey>("/devices", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  setDeviceEnabled: (deviceId: string, enabled: boolean) =>
    request<{ device_id: string; enabled: boolean }>(
      `/devices/${encodeURIComponent(deviceId)}/enabled?enabled=${enabled}`,
      { method: "PATCH" },
    ),

  deleteDevice: (deviceId: string) =>
    request<void>(`/devices/${encodeURIComponent(deviceId)}`, { method: "DELETE" }),

  getMetricCatalog: (deviceId: string) =>
    request<string[]>(`/devices/${encodeURIComponent(deviceId)}/metrics`),

  getSeries: (deviceId: string, metric: string, hours: number) =>
    request<MetricSeries>(
      `/devices/${encodeURIComponent(deviceId)}/metrics/${encodeURIComponent(metric)}?${rangeQuery(hours)}`,
    ),

  listAnomalies: (deviceId: string, hours = 24) =>
    request<Anomaly[]>(`/devices/${encodeURIComponent(deviceId)}/anomalies?${rangeQuery(hours)}`),
};
