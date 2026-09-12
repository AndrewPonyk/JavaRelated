import { describe, it, expect, beforeEach, vi } from 'vitest';

class LocalStorageMock {
  private store: Record<string, string> = {};
  clear() {
    this.store = {};
  }
  getItem(key: string) {
    return this.store[key] || null;
  }
  setItem(key: string, value: string) {
    this.store[key] = String(value);
  }
  removeItem(key: string) {
    delete this.store[key];
  }
}

globalThis.localStorage = new LocalStorageMock() as unknown as Storage;

import { apiClient } from './api/client';
import { fetchUsageMetrics, fetchCapacityPrediction } from './api/usage';
import { listTenants } from './api/tenants';

describe('Frontend API Client & Services', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('injects active tenant and viewer country headers into requests', async () => {
    localStorage.setItem('saas_active_tenant', 'tenant-beta-growth');
    localStorage.setItem('saas_active_country', 'DE');

    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ status: 'HEALTHY' }),
    });
    globalThis.fetch = fetchMock;

    await apiClient('/health');

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [calledUrl, calledOptions] = fetchMock.mock.calls[0];
    expect(calledUrl).toContain('/health');
    expect(calledOptions.headers['X-Tenant-Id']).toBe('tenant-beta-growth');
    expect(calledOptions.headers['CloudFront-Viewer-Country']).toBe('DE');
  });

  it('handles HTTP error responses with status and error code', async () => {
    const errorResponse = {
      ok: false,
      status: 403,
      statusText: 'Forbidden',
      json: async () => ({ error: 'GEOLOCATION_RESTRICTED', message: 'Access denied from KP' }),
    };
    globalThis.fetch = vi.fn().mockResolvedValue(errorResponse);

    await expect(apiClient('/v1/usage')).rejects.toThrow('Access denied from KP');
  });

  it('fetches usage metrics with simulated fallback', async () => {
    const mockMetrics = {
      tenant_id: 'tenant-test',
      metric: 'api_calls',
      period: '2026-09',
      total_count: 50000,
      quota_limit: 100000,
      percent_consumed: 50.0,
      warning_threshold_exceeded: false,
      last_updated: '2026-09-09T00:00:00Z',
    };
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockMetrics,
    });

    const data = await fetchUsageMetrics('api_calls');
    expect(data.total_count).toBe(50000);
    expect(data.percent_consumed).toBe(50.0);
  });

  it('fetches capacity predictions with forecasted growth', async () => {
    const mockPrediction = {
      tenant_id: 'tenant-test',
      metric: 'api_calls',
      historical_30_days_sum: 50000,
      forecast_next_7_days: 14000,
      forecast_next_30_days: 62000,
      model_version: 'deepar-v2',
      anomaly_risk: 'LOW',
      recommended_tier_upgrade: false,
      generated_at: '2026-09-09T00:00:00Z',
    };
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockPrediction,
    });

    const pred = await fetchCapacityPrediction('api_calls');
    expect(pred.forecast_next_7_days).toBe(14000);
    expect(pred.anomaly_risk).toBe('LOW');
  });

  it('lists tenants successfully', async () => {
    const mockTenants = {
      tenants: [
        {
          tenant_id: 'tenant-1',
          name: 'Alpha',
          tier: 'ENTERPRISE',
          status: 'ACTIVE',
          contact_email: 'a@example.com',
          allowed_countries: ['US'],
          monthly_quota: 1000000,
          created_at: '2026-01-01',
          updated_at: '2026-01-01',
        },
      ],
      total: 1,
    };
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockTenants,
    });

    const res = await listTenants();
    expect(res.total).toBe(1);
    expect(res.tenants[0].name).toBe('Alpha');
  });
});
