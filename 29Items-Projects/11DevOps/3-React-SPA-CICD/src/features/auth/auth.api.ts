import { z } from 'zod';

import { api, tokenStore } from '@/api/httpClient';

// Schemas track contracts/openapi.yaml — the shared contract with the backend team.

export const userSchema = z.object({
  id: z.string(),
  email: z.string().email(),
  displayName: z.string(),
  roles: z.array(z.enum(['customer', 'admin'])),
});
export type User = z.infer<typeof userSchema>;

const loginResponseSchema = z.object({
  user: userSchema,
  accessToken: z.string().min(1),
});

/** Client-side validation — UX only; the API re-validates authoritatively (§2.5). */
export const credentialsSchema = z.object({
  email: z.string().email('Enter a valid e-mail address'),
  password: z.string().min(8, 'Password must be at least 8 characters'),
});
export type Credentials = z.infer<typeof credentialsSchema>;

export async function login(credentials: Credentials): Promise<User> {
  const { user, accessToken } = await api.post('/v1/auth/login', credentials, {
    schema: loginResponseSchema,
    auth: false,
  });
  tokenStore.set(accessToken);
  return user;
}

export async function logout(): Promise<void> {
  try {
    await api.post('/v1/auth/logout');
  } finally {
    tokenStore.clear(); // local sign-out must succeed even if the API call fails
  }
}

export async function fetchCurrentUser(): Promise<User> {
  return api.get('/v1/auth/me', { schema: userSchema });
}

// Token refresh lives inside src/api/httpClient.ts (tryRefreshSession): every 401 gets one
// silent refresh + replay transparently, so features never handle token expiry themselves.
