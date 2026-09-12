import { http, HttpResponse } from 'msw';
import { z } from 'zod';

import { db } from '../db/store';

/**
 * Mock implementation of the auth endpoints in contracts/openapi.yaml.
 * Note the deliberate SERVER-side zod validation: mocks model a backend that never
 * trusts the client, so tests exercise real 400/401 paths.
 * Wildcard origins ('*'/v1/…) match any VITE_API_BASE_URL.
 */

const loginBodySchema = z.object({
  email: z.string().email(),
  password: z.string().min(8),
});

function publicUser(user: (typeof db.users)[number]) {
  return {
    id: user.id,
    email: user.email,
    displayName: user.displayName,
    roles: user.roles,
  };
}

function bearerToken(request: Request): string | null {
  const header = request.headers.get('Authorization');
  return header?.startsWith('Bearer ') ? header.slice('Bearer '.length) : null;
}

export const authHandlers = [
  http.post('*/v1/auth/login', async ({ request }) => {
    const parsed = loginBodySchema.safeParse(await request.json().catch(() => null));
    if (!parsed.success) {
      return HttpResponse.json(
        {
          code: 'VALIDATION_ERROR',
          message: 'Invalid credentials payload',
          details: parsed.error.flatten(),
        },
        { status: 400 },
      );
    }

    const user = db.users.find((u) => u.email === parsed.data.email);
    if (!user || user.password !== parsed.data.password) {
      return HttpResponse.json(
        { code: 'INVALID_CREDENTIALS', message: 'E-mail or password is incorrect' },
        { status: 401 },
      );
    }

    const accessToken = `mock-token-${user.id}`;
    db.validTokens.add(accessToken);
    // Models the real API setting the httpOnly refresh cookie.
    db.sessionActive = true;

    return HttpResponse.json({ user: publicUser(user), accessToken });
  }),

  http.get('*/v1/auth/me', ({ request }) => {
    const token = bearerToken(request);
    if (!token || !db.validTokens.has(token)) {
      return HttpResponse.json(
        { code: 'UNAUTHENTICATED', message: 'No valid session' },
        { status: 401 },
      );
    }
    const user = db.users.find((u) => `mock-token-${u.id}` === token);
    if (!user) {
      return HttpResponse.json(
        { code: 'UNAUTHENTICATED', message: 'Unknown user' },
        { status: 401 },
      );
    }
    return HttpResponse.json(publicUser(user));
  }),

  http.post('*/v1/auth/logout', ({ request }) => {
    const token = bearerToken(request);
    if (token) db.validTokens.delete(token);
    db.sessionActive = false; // real API clears the refresh cookie + revokes the session
    return new HttpResponse(null, { status: 204 });
  }),

  // Silent refresh: the httpClient calls this on 401 and replays the original request
  // (docs/ARCHITECTURE.md §2.3). The real API validates the httpOnly refresh cookie;
  // the mock models that with db.sessionActive.
  http.post('*/v1/auth/refresh', () => {
    if (!db.sessionActive) {
      return HttpResponse.json(
        { code: 'UNAUTHENTICATED', message: 'No refresh session' },
        { status: 401 },
      );
    }
    const user = db.users[0];
    if (!user) {
      return HttpResponse.json(
        { code: 'UNAUTHENTICATED', message: 'Unknown user' },
        { status: 401 },
      );
    }
    const accessToken = `mock-token-${user.id}`;
    db.validTokens.add(accessToken);
    return HttpResponse.json({ accessToken });
  }),
];
