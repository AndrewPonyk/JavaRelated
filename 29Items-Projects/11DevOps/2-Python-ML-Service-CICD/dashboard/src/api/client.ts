import type {
  ABConfig,
  ABConfigUpdate,
  DriftReportRecord,
  DriftSummary,
  ModelListResponse,
  PredictionRequest,
  PredictionResponse,
  ProblemDetail,
  PromoteResponse,
} from "./types";

/** Error carrying the RFC 7807 problem document returned by the API. */
export class ApiError extends Error {
  readonly status: number;
  readonly title: string;
  readonly detail: string;
  readonly problem: ProblemDetail | null;

  constructor(status: number, title: string, detail: string, problem: ProblemDetail | null) {
    super(`${status} ${title}: ${detail}`);
    this.status = status;
    this.title = title;
    this.detail = detail;
    this.problem = problem;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { "Content-Type": "application/json", ...(init?.headers ?? {}) },
  });
  if (!response.ok) {
    let problem: ProblemDetail | null = null;
    try {
      problem = (await response.json()) as ProblemDetail;
    } catch {
      problem = null;
    }
    throw new ApiError(
      response.status,
      problem?.title ?? response.statusText,
      problem?.detail ?? "request failed",
      problem,
    );
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export const api = {
  getModels: (signal?: AbortSignal) =>
    request<ModelListResponse>("/api/v1/models", { signal }),

  getABConfig: (signal?: AbortSignal) =>
    request<ABConfig>("/api/v1/models/ab-config", { signal }),

  updateABConfig: (update: ABConfigUpdate) =>
    request<ABConfig>("/api/v1/models/ab-config", {
      method: "PUT",
      body: JSON.stringify(update),
    }),

  promote: (version: string, alias: "champion" | "challenger") =>
    request<PromoteResponse>("/api/v1/models/promote", {
      method: "POST",
      body: JSON.stringify({ version, alias }),
    }),

  getDrift: (signal?: AbortSignal) =>
    request<DriftSummary>("/api/v1/admin/drift", { signal }),

  getDriftReports: (signal?: AbortSignal) =>
    request<{ items: DriftReportRecord[] }>("/api/v1/admin/drift/reports?limit=30", {
      signal,
    }),

  createPrediction: (payload: PredictionRequest) =>
    request<PredictionResponse>("/api/v1/predictions", {
      method: "POST",
      body: JSON.stringify(payload),
    }),
};
