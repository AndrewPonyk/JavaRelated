import { apiGet, apiPost } from './client';
import type { AccountType, AuthUser, LoginResponse } from '@/types/auth';

/** Exchange credentials for a JWT (handled by the Symfony `login` firewall). */
export function login(email: string, password: string): Promise<LoginResponse> {
  return apiPost<LoginResponse>('/auth/login', { email, password });
}

export interface RegisterPayload {
  email: string;
  password: string;
  accountType: AccountType;
}

/** Public self-service registration. Returns the created account. */
export function register(payload: RegisterPayload): Promise<AuthUser> {
  return apiPost<AuthUser>('/auth/register', payload);
}

/** The authenticated user's identity, decoded from their JWT server-side. */
export function fetchMe(): Promise<AuthUser> {
  return apiGet<AuthUser>('/auth/me');
}
