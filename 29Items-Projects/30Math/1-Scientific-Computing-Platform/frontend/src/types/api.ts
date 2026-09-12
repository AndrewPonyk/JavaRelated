/**
 * API contract types — mirror backend/app/schemas/*.py.
 * Field names are snake_case on purpose: they match the wire format.
 */

export interface SolveRequest {
  expression: string;
  variable?: string;
}

export interface SolveResponse {
  equation_latex: string;
  variable: string;
  solutions: string[];
  solutions_latex: string[];
  steps_latex: string[];
  derivation_latex: string;
  cached: boolean;
}

export interface SolveQueuedResponse {
  computation_id: string;
  status: ComputationStatus;
  detail: string;
}

export type SolveOutcome =
  { kind: "result"; result: SolveResponse } | { kind: "queued"; queued: SolveQueuedResponse };

export interface CalculusResponse {
  input_latex: string;
  result_text: string;
  result_latex: string;
  operation: string;
}

export interface RenderResponse {
  latex: string;
}

export interface ClassifyResponse {
  label: string;
  confidence: number;
  source: "heuristic" | "sagemaker";
}

// --- Auth ---

export interface UserRead {
  id: string;
  email: string;
  role: "student" | "instructor" | "admin";
  created_at: string;
}

// --- Computations ---

export type ComputationKind = "symbolic_solve" | "integral" | "ode" | "plot" | "ml_classify";
export type ComputationStatus = "queued" | "running" | "succeeded" | "failed";

export interface ComputationRead {
  id: string;
  title: string;
  kind: ComputationKind;
  status: ComputationStatus;
  input_payload: Record<string, unknown>;
  result_payload: Record<string, unknown> | null;
  error_code: string | null;
  created_at: string;
  completed_at: string | null;
}

export interface Page<T> {
  items: T[];
  total: number;
  limit: number;
  offset: number;
}

/** RFC 7807 problem details — the shape of every backend error. */
export interface Problem {
  type: string;
  title: string;
  status: number;
  detail: string;
  request_id: string;
}
