// Typed API client: a thin fetch wrapper that centralizes base URL, auth
// headers, error normalization, and JSON parsing. Components/hooks never call
// fetch() directly.
import type { DriftReport, Experiment, ModelVersion, Page } from "../types";

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "/api";

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const token = localStorage.getItem("access_token") ?? "";
  const resp = await fetch(`${BASE_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
      ...(init?.headers ?? {}),
    },
  });

  if (!resp.ok) {
    // Backend returns RFC 7807 problem+json.
    const problem = await resp.json().catch(() => ({ detail: resp.statusText }));
    throw new ApiError(resp.status, problem.detail ?? "Request failed");
  }
  return (await resp.json()) as T;
}

export const api = {
  listExperiments: (limit = 50, offset = 0) =>
    request<Page<Experiment>>(`/experiments?limit=${limit}&offset=${offset}`),
  listModelVersions: (name: string) =>
    request<ModelVersion[]>(`/models/${encodeURIComponent(name)}/versions`),
  promoteModel: (name: string, version: number, stage: string) =>
    request<ModelVersion>(`/models/${encodeURIComponent(name)}/versions/${version}/stage`, {
      method: "POST",
      body: JSON.stringify({ stage, archive_existing: true }),
    }),
  latestDrift: (name: string) =>
    request<DriftReport>(`/drift/${encodeURIComponent(name)}/latest`),
};
