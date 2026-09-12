// Field names mirror the API's JSON (snake_case) — no mapping layer in the stub.

export interface CurrentMetric {
  metric: string;
  value: number;
  window_start_ms: number;
  window_ms: number;
}

export interface MetricHistoryPoint {
  metric: string;
  bucket_start: string; // ISO timestamp
  value: number;
}

/** Dashboard view model: a metric tile with presentation state. */
export interface MetricTile extends CurrentMetric {
  label: string;
  stale: boolean;
}
