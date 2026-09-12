// Shared domain types mirroring the backend Pydantic contracts.
// Keep in sync with backend/src/api/schemas (consider codegen from OpenAPI).

export type ModelStage = "None" | "Staging" | "Production" | "Archived";

export interface ModelVersion {
  name: string;
  version: number;
  stage: ModelStage;
  runId: string;
  framework: string;
}

export interface Experiment {
  id: string;
  name: string;
  description: string | null;
  tags: Record<string, string>;
  createdAt: string;
}

export interface DriftReport {
  modelName: string;
  method: "PSI" | "KS" | "KL";
  score: number;
  threshold: number;
  drifted: boolean;
  evaluatedFeatures: number;
}

// Paginated response envelope (mirrors backend schemas/common.py Page[T]).
export interface Page<T> {
  items: T[];
  total: number;
  limit: number;
  offset: number;
}

// Discriminated union for request lifecycle, used by data-fetching hooks.
export type AsyncState<T> =
  | { status: "idle" }
  | { status: "loading" }
  | { status: "error"; error: string }
  | { status: "success"; data: T };
