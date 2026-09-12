export type Dataset = {
  id: string;
  name: string;
  description?: string | null;
  source_type: "vector" | "raster" | "classification";
  metadata_json: Record<string, unknown>;
  created_at?: string | null;
  updated_at?: string | null;
};

export type DatasetFeature = {
  id: string;
  dataset_id: string;
  properties: Record<string, unknown>;
  geometry: {
    type: string;
    coordinates: unknown;
  };
  created_at?: string | null;
};

export type Layer = {
  id: string;
  dataset_id: string;
  name: string;
  layer_type: "wms" | "wfs" | "deckgl" | "classification";
  style: string;
  is_public: boolean;
};

export type ClassificationJob = {
  id: string;
  dataset_id: string;
  status: "queued" | "running" | "succeeded" | "failed";
  model_version?: string | null;
  metrics: Record<string, unknown>;
  artifact_uri?: string | null;
  error_message?: string | null;
};

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8000";

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      "Content-Type": "application/json",
      ...options.headers,
    },
    ...options,
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => null);
    const message = payload?.error?.message ?? `Request failed: ${response.status}`;
    throw new Error(message);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export async function fetchDatasets(signal?: AbortSignal): Promise<Dataset[]> {
  return request<Dataset[]>("/api/v1/datasets", { signal });
}

export async function createDataset(input: {
  name: string;
  description?: string;
  source_type: Dataset["source_type"];
}): Promise<Dataset> {
  return request<Dataset>("/api/v1/datasets", {
    method: "POST",
    body: JSON.stringify({ ...input, metadata_json: { source: "frontend" } }),
  });
}

export async function fetchFeatures(datasetId: string, signal?: AbortSignal): Promise<DatasetFeature[]> {
  return request<DatasetFeature[]>(`/api/v1/datasets/${datasetId}/features`, { signal });
}

export async function createPointFeature(input: {
  datasetId: string;
  longitude: number;
  latitude: number;
  properties: Record<string, unknown>;
}): Promise<DatasetFeature> {
  return request<DatasetFeature>(`/api/v1/datasets/${input.datasetId}/features`, {
    method: "POST",
    body: JSON.stringify({
      properties: input.properties,
      geometry: {
        type: "Point",
        coordinates: [input.longitude, input.latitude],
      },
    }),
  });
}

export async function fetchLayers(datasetId?: string, signal?: AbortSignal): Promise<Layer[]> {
  const query = datasetId ? `?dataset_id=${datasetId}` : "";
  return request<Layer[]>(`/api/v1/layers${query}`, { signal });
}

export async function createLayer(input: {
  dataset_id: string;
  name: string;
  layer_type: Layer["layer_type"];
  style: string;
  is_public: boolean;
}): Promise<Layer> {
  return request<Layer>("/api/v1/layers", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export async function runClassification(datasetId: string): Promise<ClassificationJob> {
  return request<ClassificationJob>("/api/v1/classification-jobs?run_immediately=false", {
    method: "POST",
    body: JSON.stringify({ dataset_id: datasetId, model_version: "rules-v1" }),
  }).then((job) => request<ClassificationJob>(`/api/v1/classification-jobs/${job.id}/run`, { method: "POST" }));
}

export async function fetchClassificationJobs(datasetId?: string, signal?: AbortSignal): Promise<ClassificationJob[]> {
  const query = datasetId ? `?dataset_id=${datasetId}` : "";
  return request<ClassificationJob[]>(`/api/v1/classification-jobs${query}`, { signal });
}

export async function fetchDatasetSummary(datasetId: string, signal?: AbortSignal): Promise<{
  dataset_id: string;
  feature_count: number;
  extent: Record<string, unknown> | null;
  classes: Record<string, number>;
}> {
  return request(`/api/v1/analysis/datasets/${datasetId}/summary`, { signal });
}

export async function searchNearby(input: {
  datasetId: string;
  longitude: number;
  latitude: number;
  radiusMeters: number;
}): Promise<DatasetFeature[]> {
  const params = new URLSearchParams({
    longitude: String(input.longitude),
    latitude: String(input.latitude),
    radius_meters: String(input.radiusMeters),
  });
  return request<DatasetFeature[]>(`/api/v1/analysis/datasets/${input.datasetId}/proximity?${params}`);
}
