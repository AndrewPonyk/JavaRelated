import { afterEach, describe, expect, it, vi } from 'vitest';

import { ApiError, portfolioApi, strategiesApi } from '../services/api';

function mockFetch(impl: () => Partial<Response>) {
  globalThis.fetch = vi.fn().mockResolvedValue(impl()) as unknown as typeof fetch;
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('api client', () => {
  it('parses a JSON list response', async () => {
    mockFetch(() => ({ ok: true, status: 200, json: async () => [{ id: '1' }, { id: '2' }] }));
    const result = await strategiesApi.list();
    expect(result).toHaveLength(2);
  });

  it('throws ApiError with status on a 404', async () => {
    mockFetch(() => ({
      ok: false,
      status: 404,
      statusText: 'Not Found',
      json: async () => ({ detail: 'strategy not found' }),
    }));
    await expect(strategiesApi.get('missing')).rejects.toMatchObject({
      name: 'ApiError',
      status: 404,
    });
  });

  it('wraps transport failures as ApiError(status=0)', async () => {
    globalThis.fetch = vi.fn().mockRejectedValue(new Error('offline')) as unknown as typeof fetch;
    await expect(portfolioApi.pnl()).rejects.toBeInstanceOf(ApiError);
  });

  it('returns undefined on 204 No Content', async () => {
    mockFetch(() => ({ ok: true, status: 204, json: async () => undefined }));
    await expect(strategiesApi.remove('1')).resolves.toBeUndefined();
  });
});
