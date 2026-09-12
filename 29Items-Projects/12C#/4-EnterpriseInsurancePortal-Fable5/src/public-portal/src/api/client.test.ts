import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, fileClaim, getCustomerSession, getMyPolicies, setCustomerSession } from './client';

describe('api client', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => vi.unstubAllGlobals());

  it('stores and clears the customer session', () => {
    expect(getCustomerSession()).toBeNull();
    setCustomerSession('abc');
    expect(getCustomerSession()).toBe('abc');
    setCustomerSession(null);
    expect(getCustomerSession()).toBeNull();
  });

  it('sends the customer identity headers when signed in', async () => {
    setCustomerSession('33333333-3333-3333-3333-333333333333');
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 200, json: () => Promise.resolve([]) });
    vi.stubGlobal('fetch', fetchMock);

    await getMyPolicies();

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    const headers = init.headers as Record<string, string>;
    expect(headers['X-Dev-Role']).toBe('Customer');
    expect(headers['X-Dev-CustomerId']).toBe('33333333-3333-3333-3333-333333333333');
  });

  it('flattens validation errors from ProblemDetails into the ApiError message', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 400,
        json: () =>
          Promise.resolve({
            title: 'Validation failed',
            errors: { ClaimedAmount: ['Claimed amount must be positive.'] },
          }),
      }),
    );

    await expect(fileClaim('p1', 'desc', -5)).rejects.toThrowError(
      /Claimed amount must be positive\./,
    );
    await expect(fileClaim('p1', 'desc', -5)).rejects.toBeInstanceOf(ApiError);
  });
});
