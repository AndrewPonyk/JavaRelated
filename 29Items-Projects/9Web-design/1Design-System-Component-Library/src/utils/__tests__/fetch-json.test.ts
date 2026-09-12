import { fetchJson, HttpError } from '../fetch-json';

describe('fetchJson', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('returns JSON payloads', async () => {
    Object.defineProperty(globalThis, 'fetch', {
      writable: true,
      value: jest.fn().mockResolvedValue({
        ok: true,
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({ ok: true })
      } satisfies Partial<Response>)
    });

    await expect(fetchJson<{ ok: boolean }>('/test')).resolves.toEqual({ ok: true });
  });

  it('throws HttpError for failed responses', async () => {
    Object.defineProperty(globalThis, 'fetch', {
      writable: true,
      value: jest.fn().mockResolvedValue({
        ok: false,
        status: 400,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({ error: { code: 'BAD_REQUEST' } })
      } satisfies Partial<Response>)
    });

    await expect(fetchJson('/bad')).rejects.toBeInstanceOf(HttpError);
  });
});
