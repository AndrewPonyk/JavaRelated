/** Mirrors of the FastAPI response/request schemas (see docs/api/openapi.json). */

export interface ModelVersionInfo {
  name: string;
  version: string;
  stage: string;
  auc: number | null;
  registered_at: string | null;
  source: string;
}

export interface ModelAliases {
  champion: string | null;
  challenger: string | null;
}

export interface ModelListResponse {
  items: ModelVersionInfo[];
  aliases: ModelAliases;
}

export interface ABConfig {
  enabled: boolean;
  traffic_split: number;
  model_name: string;
  champion_version: string | null;
  challenger_version: string | null;
}

export interface ABConfigUpdate {
  enabled?: boolean;
  traffic_split?: number;
}

export interface PromoteResponse {
  version: string;
  alias: string;
  previous: string | null;
}

export interface DriftFeature {
  feature_name: string;
  psi_score: number;
  threshold: number;
  drift_detected: boolean;
}

export interface DriftSummary {
  status: "ok" | "drift_detected" | "insufficient_data";
  features: DriftFeature[];
  evaluated_rows: number;
  retraining_triggered: boolean;
  detail: string | null;
}

export interface DriftReportRecord {
  id: number;
  feature_name: string;
  psi_score: number;
  threshold: number;
  drift_detected: boolean;
  window_start: string;
  window_end: string;
  created_at: string;
}

export interface PredictionRequest {
  transaction_id: string;
  account_id: string;
  amount: number;
  merchant_category: string;
  timestamp: string;
  features?: Record<string, number>;
}

export interface PredictionResponse {
  transaction_id: string;
  model_version: string;
  variant: string;
  fraud_probability: number;
  is_fraud: boolean;
  latency_ms: number;
}

export interface ValidationIssue {
  loc: (string | number)[];
  msg: string;
  type: string;
}

export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail?: string | null;
  errors?: ValidationIssue[];
}
