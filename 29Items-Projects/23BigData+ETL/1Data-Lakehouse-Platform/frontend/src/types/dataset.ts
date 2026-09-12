// Mirrors services/api/app/schemas/dataset.py — keep the two in sync when the
// API contract changes (single source of truth is the Pydantic schema module).

export type DatasetLayer = "bronze" | "silver" | "gold";

export interface Dataset {
  id: string;
  name: string;
  layer: DatasetLayer;
  description: string | null;
  owner_email: string;
  s3_path: string;
  created_at: string;
  updated_at: string;
}

export interface DatasetList {
  items: Dataset[];
  total: number;
  limit: number;
  offset: number;
}

export interface DatasetCreatePayload {
  name: string;
  layer: DatasetLayer;
  description: string | null;
  owner_email: string;
  s3_path: string;
}

export interface DatasetVersion {
  id: string;
  dataset_id: string;
  version: number;
  schema_json: Record<string, unknown>;
  row_count: number | null;
  created_at: string;
}

export interface AuditEvent {
  id: string;
  occurred_at: string;
  actor: string;
  action: string;
  entity_type: string;
  entity_id: string;
  details: Record<string, unknown> | null;
}

export interface AuditEventList {
  items: AuditEvent[];
  total: number;
  limit: number;
  offset: number;
}

export interface Preview {
  columns: string[];
  rows: unknown[][];
  row_count: number;
  source: string;
}
