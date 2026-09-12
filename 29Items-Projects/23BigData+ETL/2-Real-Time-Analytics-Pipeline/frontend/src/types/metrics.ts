// TS mirrors of the API DTOs (services/analytics-api …/dto) and SSE payloads.
// The OpenAPI spec is served live at /v3/api-docs (springdoc); generating these
// from it is a possible later refinement.

export interface MetricDefinition {
  metricKey: string;
  displayName: string;
  unit: string | null;
  description: string | null;
  createdAt: string; // ISO-8601
}

export interface CreateMetricRequest {
  metricKey: string;
  displayName: string;
  unit: string | null;
  description: string | null;
}

export interface AggregatePoint {
  windowStart: string; // ISO-8601
  count: number;
  sum: number;
  min: number;
  max: number;
  avg: number;
}

/** SSE 'aggregate' payload — raw topic JSON (one record per metric series). */
export interface LiveAggregate {
  metricKey: string;
  windowSize: string;
  windowStart: number; // epoch millis on the wire
  windowEnd: number;
  count: number;
  sum: number;
  min: number;
  max: number;
  avg: number;
  dimensions: Record<string, string>;
}

export type WindowSize = '1s' | '10s' | '1m' | '5m' | '1h';

export type AlertSeverity = 'warning' | 'serious' | 'critical';

export interface AnomalyAlert {
  alertId: string;
  metricKey: string;
  severity: AlertSeverity;
  score: number;
  observed: number;
  expected: number;
  windowStart: string;
  detectedAt: string;
  status: 'open' | 'acknowledged' | 'resolved';
  ackedBy: string | null;
}

export type StreamStatus = 'connecting' | 'live' | 'paused';
