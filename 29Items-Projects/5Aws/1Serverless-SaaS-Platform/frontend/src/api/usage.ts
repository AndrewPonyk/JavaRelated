import { apiClient } from './client';
import {
  CapacityPrediction,
  InvoicePreview,
  UsageHistoryResponse,
  UsageMetrics,
} from '../types/usage';

export async function fetchUsageMetrics(metric: string = 'api_calls'): Promise<UsageMetrics> {
  return apiClient<UsageMetrics>(`/v1/usage?metric=${encodeURIComponent(metric)}`);
}

export async function fetchUsageHistory(metric: string = 'api_calls', days: number = 30): Promise<UsageHistoryResponse> {
  return apiClient<UsageHistoryResponse>(`/v1/usage/history?metric=${encodeURIComponent(metric)}&days=${days}`);
}

export async function fetchCapacityPrediction(metric: string = 'api_calls'): Promise<CapacityPrediction> {
  return apiClient<CapacityPrediction>(`/v1/predictions/capacity?metric=${encodeURIComponent(metric)}`);
}

export async function fetchInvoicePreview(): Promise<InvoicePreview> {
  return apiClient<InvoicePreview>('/v1/billing');
}

export async function fetchInvoicesList(): Promise<{ invoices: InvoicePreview[]; total: number }> {
  return apiClient<{ invoices: InvoicePreview[]; total: number }>('/v1/billing/invoices');
}

export async function ingestUsageEvent(metric: string, count: number = 1): Promise<{ status: string; processed_count: number }> {
  return apiClient<{ status: string; processed_count: number }>('/v1/usage/events', {
    method: 'POST',
    body: JSON.stringify({
      metric,
      count,
      idempotency_key: `evt-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`,
    }),
  });
}
