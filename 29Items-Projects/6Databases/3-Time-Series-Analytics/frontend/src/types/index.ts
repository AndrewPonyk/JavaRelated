/** API contract types — mirror backend/app/schemas. */

export interface Device {
  device_id: string;
  name: string;
  site: string;
  device_type: string;
  enabled: boolean;
  created_at: string | null;
}

/** Returned exactly once, at registration — api_key is never shown again. */
export interface DeviceWithKey extends Device {
  api_key: string;
}

export interface SeriesPoint {
  ts: string; // ISO-8601 UTC
  value: number;
}

export interface MetricSeries {
  device_id: string;
  metric: string;
  source: "raw" | "rollup_1h";
  points: SeriesPoint[];
  decimated: boolean;
}

export interface Anomaly {
  device_id: string;
  metric: string;
  ts: string;
  value: number;
  expected: number | null;
  lower: number | null;
  upper: number | null;
  score: number;
  method: "prophet" | "zscore";
}

export interface TokenResponse {
  access_token: string;
  token_type: "bearer";
  expires_in: number;
  roles: string[];
}
