// Typed API client for the Image Classification Service.

export interface LabelPrediction {
  name: string;
  score: number;
}

export interface ClassifyResponse {
  labels: LabelPrediction[];
  model_version: string;
  cached: boolean;
  latency_ms: number | null;
}

export interface Category {
  id: number;
  name: string;
  parent_id: number | null;
  description: string | null;
}

export interface TokenResponse {
  access_token: string;
  token_type: string;
  expires_in: number;
  scopes: string[];
}

interface ApiErrorBody {
  error?: { code: string; message: string; request_id?: string };
}

// Empty in dev (Vite proxy); set VITE_API_BASE_URL for production builds.
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code?: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

async function request<T>(path: string, init: RequestInit = {}, token?: string): Promise<T> {
  const headers = new Headers(init.headers);
  if (token) headers.set('Authorization', `Bearer ${token}`);

  const res = await fetch(`${BASE_URL}${path}`, { ...init, headers });

  if (res.status === 204) return undefined as T;

  const text = await res.text();
  const body = text ? JSON.parse(text) : undefined;

  if (!res.ok) {
    const err = (body as ApiErrorBody)?.error;
    throw new ApiError(err?.message ?? `Request failed (${res.status})`, res.status, err?.code);
  }
  return body as T;
}

export async function login(username: string, password: string): Promise<TokenResponse> {
  return request<TokenResponse>('/auth/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
}

export async function classifyImage(file: File, token?: string): Promise<ClassifyResponse> {
  const form = new FormData();
  form.append('file', file);
  return request<ClassifyResponse>('/classify', { method: 'POST', body: form }, token);
}

export async function listCategories(token?: string): Promise<Category[]> {
  return request<Category[]>('/categories', {}, token);
}

export async function createCategory(
  payload: { name: string; description?: string; parent?: string },
  token?: string,
): Promise<Category> {
  return request<Category>(
    '/categories',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
    token,
  );
}

export async function deleteCategory(id: number, token?: string): Promise<void> {
  return request<void>(`/categories/${id}`, { method: 'DELETE' }, token);
}
