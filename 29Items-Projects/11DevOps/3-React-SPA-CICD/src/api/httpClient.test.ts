import { delay, http, HttpResponse } from 'msw';
import { z } from 'zod';

import { api, ApiError, tokenStore } from './httpClient';
import { db } from '../../mocks/db/store';
import { server } from '../../mocks/server';

describe('httpClient', () => {
  it('performs a GET and returns the parsed JSON body', async () => {
    const announcements = await api.get<unknown[]>('/v1/announcements');
    expect(Array.isArray(announcements)).toBe(true);
    expect(announcements.length).toBeGreaterThan(0);
  });

  it('validates responses against the provided schema and returns typed data', async () => {
    const schema = z.array(z.object({ id: z.string(), title: z.string() }));
    const result = await api.get('/v1/announcements', { schema });
    expect(result[0]?.id).toBe('ann-1');
  });

  it('throws CONTRACT_VIOLATION when the response does not match the schema', async () => {
    const wrongSchema = z.object({ definitely: z.string(), not: z.string() });

    await expect(api.get('/v1/announcements', { schema: wrongSchema })).rejects.toMatchObject({
      name: 'ApiError',
      code: 'CONTRACT_VIOLATION',
    });
  });

  it('maps problem-JSON error bodies onto ApiError fields', async () => {
    await expect(
      api.post('/v1/auth/login', { email: 'not-an-email', password: 'x' }, { auth: false }),
    ).rejects.toMatchObject({
      status: 400,
      code: 'VALIDATION_ERROR',
      message: 'Invalid credentials payload',
    });
  });

  it('returns undefined for 204 No Content responses', async () => {
    await expect(api.post('/v1/auth/logout')).resolves.toBeUndefined();
  });

  it('attaches the bearer token from the token store', async () => {
    let seenAuthorization: string | null = null;
    server.use(
      http.get('*/v1/announcements', ({ request }) => {
        seenAuthorization = request.headers.get('Authorization');
        return HttpResponse.json([]);
      }),
    );

    tokenStore.set('my-token');
    await api.get('/v1/announcements');
    expect(seenAuthorization).toBe('Bearer my-token');
  });

  it('wraps network failures as ApiError NETWORK_ERROR', async () => {
    server.use(http.get('*/v1/announcements', () => HttpResponse.error()));

    await expect(api.get('/v1/announcements')).rejects.toMatchObject({
      code: 'NETWORK_ERROR',
      status: 0,
    });
  });

  it('aborts slow requests as ApiError TIMEOUT', async () => {
    server.use(
      http.get('*/v1/announcements', async () => {
        await delay(500);
        return HttpResponse.json([]);
      }),
    );

    await expect(api.get('/v1/announcements', { timeoutMs: 50 })).rejects.toMatchObject({
      code: 'TIMEOUT',
    });
  });

  describe('silent refresh + replay on 401', () => {
    it('refreshes an expired access token and replays the original request once', async () => {
      db.sessionActive = true; // server-side session alive (refresh cookie valid)
      tokenStore.set('expired-token'); // but our access token is stale

      const user = await api.get<{ email: string }>('/v1/auth/me');

      expect(user.email).toBe('demo@example.com');
      expect(tokenStore.accessToken).toBe('mock-token-user-1'); // replaced by refresh
    });

    it('shares one refresh round-trip between concurrent 401s (single-flight)', async () => {
      db.sessionActive = true;
      tokenStore.set('expired-token');

      let refreshCalls = 0;
      server.use(
        http.post('*/v1/auth/refresh', () => {
          refreshCalls += 1;
          const token = 'mock-token-user-1';
          db.validTokens.add(token);
          return HttpResponse.json({ accessToken: token });
        }),
      );

      const [a, b] = await Promise.all([
        api.get<{ email: string }>('/v1/auth/me'),
        api.get<{ email: string }>('/v1/auth/me'),
      ]);

      expect(a.email).toBe('demo@example.com');
      expect(b.email).toBe('demo@example.com');
      expect(refreshCalls).toBe(1);
    });

    it('gives up after a failed refresh: clears the token and surfaces the 401', async () => {
      db.sessionActive = false; // no server-side session → refresh must 401
      tokenStore.set('expired-token');

      await expect(api.get('/v1/auth/me')).rejects.toMatchObject({
        status: 401,
        code: 'UNAUTHENTICATED',
      });
      expect(tokenStore.accessToken).toBeNull();
    });

    it('does not loop: a 401 on the replayed request is surfaced, not retried again', async () => {
      db.sessionActive = true;
      tokenStore.set('expired-token');

      let meCalls = 0;
      server.use(
        http.get('*/v1/auth/me', () => {
          meCalls += 1;
          return HttpResponse.json({ code: 'UNAUTHENTICATED', message: 'nope' }, { status: 401 });
        }),
      );

      await expect(api.get('/v1/auth/me')).rejects.toMatchObject({ status: 401 });
      expect(meCalls).toBe(2); // original + exactly one replay
    });
  });

  it('exposes ApiError with its structured fields', () => {
    const error = new ApiError(422, 'SOME_CODE', 'message', { field: 'x' });
    expect(error).toBeInstanceOf(Error);
    expect(error.status).toBe(422);
    expect(error.code).toBe('SOME_CODE');
    expect(error.details).toEqual({ field: 'x' });
  });
});
