import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError, request } from './apiClient';

const mockFetch = vi.fn();
vi.stubGlobal('fetch', mockFetch);

describe('request', () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it('prefixes the base URL and parses JSON on success', async () => {
    mockFetch.mockResolvedValueOnce(
      new Response(JSON.stringify({ hello: 'world' }), { status: 200 }),
    );
    const result = await request<{ hello: string }>('/thing');
    expect(result).toEqual({ hello: 'world' });
    expect(mockFetch).toHaveBeenCalledWith('/api/thing', expect.anything());
  });

  it('throws ApiError with the server envelope message on failure', async () => {
    mockFetch.mockResolvedValueOnce(
      new Response(JSON.stringify({ error: { code: 'X', message: 'nope' } }), { status: 400 }),
    );
    try {
      await request('/thing');
      expect.unreachable('should have thrown');
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError);
      expect((error as ApiError).status).toBe(400);
      expect((error as ApiError).message).toBe('nope');
    }
  });

  it('carries status and raw text for non-JSON error bodies', async () => {
    mockFetch.mockResolvedValueOnce(new Response('plain failure', { status: 503 }));
    try {
      await request('/thing');
      expect.unreachable('should have thrown');
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError);
      expect((error as ApiError).status).toBe(503);
      expect((error as ApiError).message).toContain('plain failure');
    }
  });

  it('returns undefined for 204 No Content', async () => {
    mockFetch.mockResolvedValueOnce(new Response(null, { status: 204 }));
    await expect(request('/thing', { method: 'DELETE' })).resolves.toBeUndefined();
  });
});
