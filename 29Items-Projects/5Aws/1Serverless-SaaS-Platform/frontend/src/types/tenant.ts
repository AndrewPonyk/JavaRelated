export type TenantTier = 'STARTER' | 'PRO' | 'ENTERPRISE';
export type TenantStatus = 'ACTIVE' | 'SUSPENDED' | 'PENDING_VERIFICATION';

export interface Tenant {
  tenant_id: string;
  name: string;
  tier: TenantTier;
  status: TenantStatus;
  contact_email: string;
  allowed_countries: string[];
  monthly_quota: number;
  created_at: string;
  updated_at: string;
}

export interface TenantUpdateRequest {
  name?: string;
  tier?: TenantTier;
  status?: TenantStatus;
  allowed_countries?: string[];
  monthly_quota?: number;
  contact_email?: string;
}

export interface TenantCreateRequest {
  name: string;
  tier: TenantTier;
  contact_email: string;
  allowed_countries: string[];
  monthly_quota: number;
}

export interface TenantUser {
  tenant_id: string;
  user_id: string;
  email: string;
  name: string;
  role: string;
  created_at: string;
}
