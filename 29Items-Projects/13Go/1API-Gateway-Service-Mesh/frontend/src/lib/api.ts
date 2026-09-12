export type Tenant = {
  id: string;
  name: string;
  status: "active" | "suspended";
  createdAt: string;
  updatedAt: string;
};

export type GatewayRoute = {
  id: string;
  tenantId: string;
  name: string;
  host: string;
  pathPrefix: string;
  methods: string[];
  upstreamService: string;
  upstreamProtocol: "http" | "grpc";
  rateLimitPerMinute: number;
  requiredScopes: string[];
  transformHeaders: Record<string, string>;
  anomalyProtection: boolean;
  createdAt: string;
  updatedAt: string;
};

export type RouteInput = Omit<GatewayRoute, "id" | "createdAt" | "updatedAt">;

export type AnomalyEvent = {
  id: string;
  routeId: string;
  tenantId: string;
  score: number;
  reason: string;
  features: Record<string, unknown>;
  observedAt: string;
};

export type TrafficFeatures = {
  routeId: string;
  tenantId: string;
  requestRate: number;
  errorRate: number;
  p95LatencyMs: number;
  bytesPerSec: number;
  deployVersion: string;
};

export type AnomalyDecision = {
  score: number;
  isAnomaly: boolean;
  reason: string;
};

type ApiEnvelope<T> = {
  data: T;
};

const apiKeyStorageKey = "gateway_admin_api_key";

export function getAdminApiKey(): string {
  return window.localStorage.getItem(apiKeyStorageKey) ?? "";
}

export function setAdminApiKey(value: string): void {
  window.localStorage.setItem(apiKeyStorageKey, value);
}

export async function listTenants(): Promise<Tenant[]> {
  return request<Tenant[]>("/api/v1/tenants");
}

export async function createTenant(input: Pick<Tenant, "name" | "status">): Promise<Tenant> {
  return request<Tenant>("/api/v1/tenants", {
    method: "POST",
    body: JSON.stringify(input)
  });
}

export async function listRoutes(tenantId?: string): Promise<GatewayRoute[]> {
  const query = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
  return request<GatewayRoute[]>(`/api/v1/routes${query}`);
}

export async function createRoute(input: RouteInput): Promise<GatewayRoute> {
  return request<GatewayRoute>("/api/v1/routes", {
    method: "POST",
    body: JSON.stringify(input)
  });
}

export async function updateRoute(id: string, input: RouteInput): Promise<GatewayRoute> {
  return request<GatewayRoute>(`/api/v1/routes/${id}`, {
    method: "PUT",
    body: JSON.stringify(input)
  });
}

export async function deleteRoute(id: string): Promise<void> {
  await request<void>(`/api/v1/routes/${id}`, { method: "DELETE" });
}

export async function listAnomalies(tenantId?: string): Promise<AnomalyEvent[]> {
  const query = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
  return request<AnomalyEvent[]>(`/api/v1/anomalies${query}`);
}

export async function scoreTraffic(input: TrafficFeatures): Promise<AnomalyDecision> {
  return request<AnomalyDecision>("/api/v1/traffic/score", {
    method: "POST",
    body: JSON.stringify(input)
  });
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      "X-Admin-API-Key": getAdminApiKey(),
      ...init.headers
    }
  });

  if (response.status === 204) {
    return undefined as T;
  }

  const body = await response.json().catch(() => null);
  if (!response.ok) {
    const message = body?.error?.message ?? `Request failed with status ${response.status}`;
    throw new Error(message);
  }

  return (body as ApiEnvelope<T>).data;
}
