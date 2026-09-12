/**
 * Central HTTP client with Cognito Bearer Token, Tenant ID, and Geolocation header injection.
 */

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8000';

export interface RequestOptions extends RequestInit {
  token?: string;
  countryOverride?: string;
  tenantOverride?: string;
}

export async function apiClient<T>(endpoint: string, options: RequestOptions = {}): Promise<T> {
  const { token, countryOverride, tenantOverride, headers = {}, ...rest } = options;

  const authToken = token || localStorage.getItem('saas_auth_token') || 'mock-dev-token';
  const activeCountry = countryOverride || localStorage.getItem('saas_active_country') || 'US';
  const activeTenant = tenantOverride || localStorage.getItem('saas_active_tenant') || 'tenant-alpha-enterprise';

  const defaultHeaders: Record<string, string> = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${authToken}`,
    'X-Tenant-Id': activeTenant,
    'CloudFront-Viewer-Country': activeCountry,
  };

  const url = `${API_BASE_URL}${endpoint}`;

  const response = await fetch(url, {
    headers: {
      ...defaultHeaders,
      ...(headers as Record<string, string>),
    },
    ...rest,
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    const message = errorData.message || `HTTP ${response.status}: ${response.statusText}`;
    const err = new Error(message) as Error & { status?: number; code?: string };
    err.status = response.status;
    err.code = errorData.error || 'API_ERROR';
    throw err;
  }

  return (await response.json()) as T;
}
