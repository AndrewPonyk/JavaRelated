// API client for Policy.Api.
//
// Auth note: in production the portal authenticates with OIDC (code + PKCE against
// Portal.Identity) and sends a Bearer token. In local development the API runs with
// its header-driven Dev auth scheme, so this client identifies the signed-in customer
// via X-Dev-* headers instead. `setCustomerSession` is the seam where the OIDC token
// plugs in later.

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:5101';
const SESSION_KEY = 'portal.customerId';

export interface PolicySummary {
  id: string;
  policyNumber: string;
  customerId: string;
  annualPremium: number;
  effectiveDate: string;
  expiryDate: string;
  status: string;
}

export interface ClaimSummary {
  id: string;
  policyId: string;
  description: string;
  claimedAmount: number;
  approvedAmount: number | null;
  status: string;
  filedAtUtc: string;
  resolvedAtUtc: string | null;
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

export function getCustomerSession(): string | null {
  return localStorage.getItem(SESSION_KEY);
}

export function setCustomerSession(customerId: string | null): void {
  if (customerId) {
    localStorage.setItem(SESSION_KEY, customerId);
  } else {
    localStorage.removeItem(SESSION_KEY);
  }
}

interface ProblemDetails {
  title?: string;
  detail?: string;
  errors?: Record<string, string[]>;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const customerId = getCustomerSession();
  const response = await fetch(`${BASE_URL}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      'X-Dev-Role': 'Customer',
      'X-Dev-User': customerId ? `customer:${customerId}` : 'customer:anonymous',
      ...(customerId ? { 'X-Dev-CustomerId': customerId } : {}),
      ...init?.headers,
    },
  });

  if (!response.ok) {
    let message = `Request failed (${response.status}).`;
    try {
      const problem = (await response.json()) as ProblemDetails;
      if (problem.errors) {
        message = Object.values(problem.errors).flat().join(' ');
      } else if (problem.detail || problem.title) {
        message = problem.detail ?? problem.title ?? message;
      }
    } catch {
      // Non-JSON error body — keep the generic message.
    }
    throw new ApiError(response.status, message);
  }

  return (await response.json()) as T;
}

export function getMyPolicies(): Promise<PolicySummary[]> {
  // The server scopes results to the authenticated customer.
  return request<PolicySummary[]>('/api/v1/policies');
}

export function getClaims(policyId: string): Promise<ClaimSummary[]> {
  return request<ClaimSummary[]>(`/api/v1/policies/${policyId}/claims`);
}

export function fileClaim(
  policyId: string,
  description: string,
  claimedAmount: number,
): Promise<ClaimSummary> {
  return request<ClaimSummary>(`/api/v1/policies/${policyId}/claims`, {
    method: 'POST',
    body: JSON.stringify({ description, claimedAmount }),
  });
}
