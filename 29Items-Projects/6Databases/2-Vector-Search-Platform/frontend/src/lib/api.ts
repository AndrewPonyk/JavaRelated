// Typed API client. Runs **server-side** (route handlers) so the backend API key never
// reaches the browser. Client components call our own /api/* proxy routes instead.

import type {
  BenchmarkRequest,
  BenchmarkResponse,
  DocumentIngestRequest,
  DocumentIngestResponse,
  DocumentListResponse,
  SearchRequest,
  SearchResponse,
} from "@/types";

const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8000/api/v1";
const BACKEND_API_KEY = process.env.BACKEND_API_KEY ?? "";

export class ApiClientError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly status: number,
  ) {
    super(message);
    this.name = "ApiClientError";
  }
}

async function request<T>(path: string, init: RequestInit): Promise<T> {
  const res = await fetch(`${BACKEND_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      "X-API-Key": BACKEND_API_KEY,
      ...(init.headers ?? {}),
    },
    cache: "no-store",
  });

  if (res.status === 204) {
    return undefined as T;
  }

  const body = (await res.json().catch(() => null)) as unknown;
  if (!res.ok) {
    const err = body as { error?: { code?: string; message?: string } } | null;
    throw new ApiClientError(
      err?.error?.code ?? "http_error",
      err?.error?.message ?? res.statusText,
      res.status,
    );
  }
  return body as T;
}

export function search(payload: SearchRequest): Promise<SearchResponse> {
  return request<SearchResponse>("/search", { method: "POST", body: JSON.stringify(payload) });
}

export function ingestDocument(
  payload: DocumentIngestRequest,
): Promise<DocumentIngestResponse> {
  return request<DocumentIngestResponse>("/documents", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function listDocuments(limit = 50, offset = 0): Promise<DocumentListResponse> {
  return request<DocumentListResponse>(`/documents?limit=${limit}&offset=${offset}`, {
    method: "GET",
  });
}

export function deleteDocument(id: string): Promise<void> {
  return request<void>(`/documents/${encodeURIComponent(id)}`, { method: "DELETE" });
}

export function runBenchmark(payload: BenchmarkRequest): Promise<BenchmarkResponse> {
  return request<BenchmarkResponse>("/benchmarks", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}
