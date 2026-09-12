import { z } from 'zod';

import { editRecipeSchema, type EditRecipe } from '../contracts/editor';

const savedProjectSchema = z.object({
  id: z.string().uuid(),
  name: z.string().min(1).max(120),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
});

const savedPresetSchema = z.object({
  id: z.string().uuid(),
  projectId: z.string().uuid().nullable(),
  name: z.string().min(1).max(80),
  recipe: editRecipeSchema as z.ZodType<EditRecipe>,
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
});

const sessionSchema = z.object({
  user: z.object({ id: z.string().uuid(), displayName: z.string().min(1).max(60) }),
  token: z.string().min(1),
});

export type SavedProject = z.infer<typeof savedProjectSchema>;
export type SavedPreset = z.infer<typeof savedPresetSchema>;

const storageKey = 'webassembly-image-editor.session-token';
const REQUEST_TIMEOUT_MS = 10_000;
let memoryToken: string | null = null;

function apiBaseUrl(): string {
  return (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '');
}

function isEnabled(): boolean {
  return import.meta.env.VITE_ENABLE_PRESET_API === 'true';
}

function storedToken(): string | null {
  if (memoryToken) return memoryToken;
  try {
    return sessionStorage.getItem(storageKey);
  } catch {
    return null;
  }
}

function saveToken(token: string): void {
  memoryToken = token;
  try {
    sessionStorage.setItem(storageKey, token);
  } catch {
    // Memory storage keeps the current tab functional when browser storage is unavailable.
  }
}

function clearToken(): void {
  memoryToken = null;
  try {
    sessionStorage.removeItem(storageKey);
  } catch {
    // There is no persisted token to clear when browser storage is unavailable.
  }
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

async function timedFetch(input: RequestInfo | URL, init: RequestInit): Promise<Response> {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  try {
    return await fetch(input, { ...init, signal: controller.signal });
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') {
      throw new ApiError(504, 'The saved-preset service timed out.');
    }
    throw new ApiError(503, 'The saved-preset service could not be reached.');
  } finally {
    window.clearTimeout(timeout);
  }
}

async function responsePayload(response: Response): Promise<unknown> {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

function errorMessage(payload: unknown): string {
  if (!payload || typeof payload !== 'object' || !('error' in payload))
    return 'The request failed.';
  const error = (payload as { error?: unknown }).error;
  if (!error || typeof error !== 'object' || !('message' in error)) return 'The request failed.';
  return typeof error.message === 'string' ? error.message : 'The request failed.';
}

async function request<T>(
  path: string,
  schema: z.ZodType<T> | null,
  init: RequestInit = {},
  retry = true,
): Promise<T> {
  if (!isEnabled()) throw new ApiError(503, 'Saved presets are disabled for this deployment.');
  const token = await sessionToken();
  const response = await timedFetch(`${apiBaseUrl()}${path}`, {
    ...init,
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${token}`,
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      ...init.headers,
    },
  });
  if (response.status === 401 && retry) {
    clearToken();
    return request(path, schema, init, false);
  }
  if (response.status === 204 && schema === null) return undefined as T;
  const payload = await responsePayload(response);
  if (!response.ok) throw new ApiError(response.status, errorMessage(payload));
  if (!schema || !payload || typeof payload !== 'object' || !('data' in payload)) {
    throw new ApiError(502, 'The saved-preset service returned an invalid response.');
  }
  const parsed = schema.safeParse((payload as { data: unknown }).data);
  if (!parsed.success) {
    throw new ApiError(502, 'The saved-preset service returned incompatible data.');
  }
  return parsed.data;
}

async function sessionToken(): Promise<string> {
  const existing = storedToken();
  if (existing) return existing;
  const response = await timedFetch(`${apiBaseUrl()}/api/auth/anonymous`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({}),
  });
  const payload = await responsePayload(response);
  if (!response.ok) {
    throw new ApiError(response.status, 'Could not start a saved-preset session.');
  }
  const parsed = z.object({ data: sessionSchema }).safeParse(payload);
  if (!parsed.success) throw new ApiError(502, 'The session service returned invalid data.');
  saveToken(parsed.data.data.token);
  return parsed.data.data.token;
}

export const presetApi = {
  enabled: isEnabled,
  listProjects: () => request('/api/projects?limit=100&offset=0', z.array(savedProjectSchema)),
  createProject: (name: string) =>
    request('/api/projects', savedProjectSchema, {
      method: 'POST',
      body: JSON.stringify({ name }),
    }),
  updateProject: (id: string, name: string) =>
    request(`/api/projects/${id}`, savedProjectSchema, {
      method: 'PUT',
      body: JSON.stringify({ name }),
    }),
  deleteProject: (id: string) => request<void>(`/api/projects/${id}`, null, { method: 'DELETE' }),
  listPresets: (projectId?: string) => {
    const parameters = new URLSearchParams({ limit: '100', offset: '0' });
    if (projectId) parameters.set('projectId', projectId);
    return request(`/api/presets?${parameters}`, z.array(savedPresetSchema));
  },
  createPreset: (input: { name: string; projectId?: string | null; recipe: EditRecipe }) =>
    request('/api/presets', savedPresetSchema, {
      method: 'POST',
      body: JSON.stringify(input),
    }),
  updatePreset: (
    id: string,
    input: Partial<{ name: string; projectId: string | null; recipe: EditRecipe }>,
  ) =>
    request(`/api/presets/${id}`, savedPresetSchema, {
      method: 'PUT',
      body: JSON.stringify(input),
    }),
  deletePreset: (id: string) => request<void>(`/api/presets/${id}`, null, { method: 'DELETE' }),
};
