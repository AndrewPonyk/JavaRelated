export type AlertSeverity = 'info' | 'warning' | 'critical';

export interface AnomalyAlert {
  alert_id: string;
  metric: string;
  value: number;
  score: number;
  severity: AlertSeverity;
  message: string;
  triggered_at: string;
  acknowledged: boolean;
}
