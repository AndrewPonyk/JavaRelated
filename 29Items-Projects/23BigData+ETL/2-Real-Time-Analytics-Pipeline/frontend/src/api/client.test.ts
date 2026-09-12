import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, acknowledgeAlert, createMetricDefinition, getMetricDefinitions } from './client';

function mockFetch(status: number, body: unknown) {
  const response = {
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
  } as Response;
  const spy = vi.fn(() => Promise.resolve(response));
  vi.stubGlobal('fetch', spy);
  return spy;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('api client', () => {
  it('returns parsed JSON on success', async () => {
    mockFetch(200, [{ metricKey: 'orders.completed' }]);

    const defs = await getMetricDefinitions();

    expect(defs).toHaveLength(1);
    expect(defs[0].metricKey).toBe('orders.completed');
  });

  it('maps problem-details bodies onto ApiError incl. field errors', async () => {
    mockFetch(400, {
      title: 'Validation failed',
      detail: undefined,
      errors: { metricKey: 'must match pattern' },
    });

    const error = await createMetricDefinition({
      metricKey: 'BAD',
      displayName: 'x',
      unit: null,
      description: null,
    }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(400);
    expect(apiError.title).toBe('Validation failed');
    expect(apiError.errors).toEqual({ metricKey: 'must match pattern' });
  });

  it('sends POST for acknowledge and surfaces 404s', async () => {
    const spy = mockFetch(404, { title: 'Not found', detail: 'Unknown alert: x' });

    const error = await acknowledgeAlert('x').catch((e: unknown) => e);

    expect(spy).toHaveBeenCalledWith('/api/v1/alerts/x/ack', expect.objectContaining({ method: 'POST' }));
    expect((error as ApiError).status).toBe(404);
    expect((error as ApiError).message).toContain('Unknown alert');
  });
});
