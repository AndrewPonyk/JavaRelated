import { apiClient } from './client';
import {
  Tenant,
  TenantCreateRequest,
  TenantUpdateRequest,
  TenantUser,
} from '../types/tenant';

export async function fetchTenantProfile(tenantId?: string): Promise<Tenant> {
  const activeId = tenantId || localStorage.getItem('saas_active_tenant') || 'tenant-alpha-enterprise';
  return apiClient<Tenant>(`/v1/tenants/${activeId}`);
}

export async function listTenants(): Promise<{ tenants: Tenant[]; total: number }> {
  return apiClient<{ tenants: Tenant[]; total: number }>('/v1/tenants?status=ACTIVE');
}

export async function createTenant(data: TenantCreateRequest): Promise<Tenant> {
  return apiClient<Tenant>('/v1/tenants', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function updateTenant(tenantId: string, data: TenantUpdateRequest): Promise<Tenant> {
  return apiClient<Tenant>(`/v1/tenants/${tenantId}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export async function listTenantUsers(tenantId: string): Promise<TenantUser[]> {
  const resp = await apiClient<{ users: TenantUser[]; total: number }>(`/v1/tenants/${tenantId}/users`);
  return resp.users;
}

export async function createTenantUser(tenantId: string, user: { name: string; email: string; role: string }): Promise<TenantUser> {
  return apiClient<TenantUser>(`/v1/tenants/${tenantId}/users`, {
    method: 'POST',
    body: JSON.stringify(user),
  });
}
