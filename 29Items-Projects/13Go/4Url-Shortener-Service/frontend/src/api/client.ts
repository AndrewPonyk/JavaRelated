const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export type CreateURLPayload = {
  originalUrl: string;
  customCode?: string;
  title?: string;
};

export type UpdateURLPayload = {
  originalUrl?: string;
  title?: string;
  expiresAt?: string;
};

export type URLResponse = {
  id: string;
  shortCode: string;
  shortUrl: string;
  originalUrl: string;
  title?: string;
  createdAt: string;
  updatedAt: string;
  expiresAt?: string;
};

export type URLListResponse = {
  items: URLResponse[];
  limit: number;
};

export type AnalyticsSummary = {
  shortCode: string;
  totalClicks: number;
  byReferrer: Record<string, number>;
  byCountry: Record<string, number>;
  byUserAgent: Record<string, number>;
  byBucket: Array<{ timestamp: string; clicks: number }>;
};

export function qrURL(code: string): string {
  return `${API_BASE_URL}/api/v1/urls/${encodeURIComponent(code)}/qr`;
}

export async function createShortURL(payload: CreateURLPayload): Promise<URLResponse> {
  return request<URLResponse>("/api/v1/urls", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export async function listShortURLs(limit = 20, apiKey = ""): Promise<URLListResponse> {
  return request<URLListResponse>(`/api/v1/urls?limit=${limit}`, {
    headers: authHeaders(apiKey)
  });
}

export async function getShortURL(code: string): Promise<URLResponse> {
  return request<URLResponse>(`/api/v1/urls/${encodeURIComponent(code)}`);
}

export async function updateShortURL(code: string, payload: UpdateURLPayload, apiKey = ""): Promise<URLResponse> {
  return request<URLResponse>(`/api/v1/urls/${encodeURIComponent(code)}`, {
    method: "PATCH",
    headers: authHeaders(apiKey),
    body: JSON.stringify(payload)
  });
}

export async function deleteShortURL(code: string, apiKey = ""): Promise<void> {
  await request<void>(`/api/v1/urls/${encodeURIComponent(code)}`, {
    method: "DELETE",
    headers: authHeaders(apiKey)
  });
}

export async function getAnalytics(code: string, apiKey = ""): Promise<AnalyticsSummary> {
  return request<AnalyticsSummary>(`/api/v1/urls/${encodeURIComponent(code)}/analytics`, {
    headers: authHeaders(apiKey)
  });
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers
  });

  if (!response.ok) {
    throw new Error(await readAPIError(response));
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

function authHeaders(apiKey: string): HeadersInit {
  return apiKey ? { "X-API-Key": apiKey } : {};
}

async function readAPIError(response: Response): Promise<string> {
  try {
    const payload = await response.json();
    return payload?.error?.message ?? `Request failed with status ${response.status}`;
  } catch {
    return `Request failed with status ${response.status}`;
  }
}
