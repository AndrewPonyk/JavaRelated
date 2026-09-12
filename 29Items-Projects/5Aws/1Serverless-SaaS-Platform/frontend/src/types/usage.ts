export interface UsageMetrics {
  tenant_id: string;
  metric: string;
  period: string;
  total_count: number;
  quota_limit: number;
  percent_consumed: number;
  warning_threshold_exceeded: boolean;
  last_updated: string;
}

export interface CapacityPrediction {
  tenant_id: string;
  metric: string;
  historical_30_days_sum: number;
  forecast_next_7_days: number;
  forecast_next_30_days: number;
  model_version: string;
  anomaly_risk: 'LOW' | 'MEDIUM' | 'HIGH';
  recommended_tier_upgrade: boolean;
  generated_at: string;
}

export interface InvoicePreview {
  tenant_id: string;
  period: string;
  tier: string;
  base_charge_cents: number;
  total_units_consumed: number;
  included_units: number;
  overage_units: number;
  overage_charge_cents: number;
  total_due_cents: number;
  currency: string;
  generated_at: string;
}

export interface UsageHistoryPoint {
  date: string;
  count: number;
  metric: string;
}

export interface UsageHistoryResponse {
  tenant_id: string;
  metric: string;
  points: UsageHistoryPoint[];
  total_points: number;
}
