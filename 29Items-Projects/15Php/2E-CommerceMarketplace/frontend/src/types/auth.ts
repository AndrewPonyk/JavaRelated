/** Auth domain types mirroring the Identity API contract. */

export interface AuthUser {
  id: string;
  email: string;
  roles: string[];
}

export interface LoginResponse {
  token: string;
}

export type AccountType = 'customer' | 'seller';
