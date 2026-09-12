// Shared DTO types mirroring the backend Pydantic schemas.

export interface Study {
  id: string;
  study_instance_uid: string;
  accession_number: string | null;
  study_date: string | null; // YYYYMMDD
  description: string | null;
  modalities: string | null;
  series_count: number;
  created_at: string;
}

export interface Series {
  id: string;
  series_instance_uid: string;
  modality: string | null;
  series_number: number | null;
  description: string | null;
  body_part: string | null;
  instance_count: number;
}

export interface InstanceMeta {
  id: string;
  sop_instance_uid: string;
  instance_number: number | null;
  rows: number | null;
  columns: number | null;
  transfer_syntax_uid: string | null;
}

export interface PageMeta {
  total: number;
  limit: number;
  offset: number;
}

export interface StudyPage {
  items: Study[];
  meta: PageMeta;
}

export interface StudyQuery {
  patient_id?: string;
  accession_number?: string;
  modality?: string;
  study_date_from?: string;
  study_date_to?: string;
  limit?: number;
  offset?: number;
}

export interface MlPrediction {
  id: string;
  model_name: string;
  model_version: string;
  predictions: Record<string, number>; // pathology -> probability
  top_label: string | null;
  top_score: number | null;
  heatmap_url: string | null;
  created_at: string;
}
