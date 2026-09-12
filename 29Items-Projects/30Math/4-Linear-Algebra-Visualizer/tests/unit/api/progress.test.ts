// @vitest-environment node
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../../api/_lib/progressRepo', () => ({
  getProgress: vi.fn(),
}));

import { getProgress } from '../../../api/_lib/progressRepo';
import handler from '../../../api/progress';
import { createMockReq, createMockRes } from './mocks';

const mockedGetProgress = vi.mocked(getProgress);

describe('GET /api/progress', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  it('responds 405 to POST', async () => {
    const mock = createMockRes();
    await handler(createMockReq({ method: 'POST' }), mock.res);
    expect(mock.statusCode).toBe(405);
    expect(mock.headers.Allow).toBe('GET');
  });

  it('responds 400 when deviceId is missing or too short', async () => {
    const missing = createMockRes();
    await handler(createMockReq({ method: 'GET' }), missing.res);
    expect(missing.statusCode).toBe(400);

    const short = createMockRes();
    await handler(createMockReq({ method: 'GET', query: { deviceId: 'abc' } }), short.res);
    expect(short.statusCode).toBe(400);
  });

  it('returns the progress rows for a known device', async () => {
    const rows = [{ topic: 'vectors' as const, mastery: 0.4, attempts: 6 }];
    mockedGetProgress.mockResolvedValueOnce(rows);

    const mock = createMockRes();
    await handler(createMockReq({ method: 'GET', query: { deviceId: 'device-123456' } }), mock.res);

    expect(mock.statusCode).toBe(200);
    expect(mock.jsonBody).toEqual({ progress: rows });
    expect(mockedGetProgress).toHaveBeenCalledWith('device-123456');
  });

  it('returns an empty list for unknown devices (client merges defaults)', async () => {
    mockedGetProgress.mockResolvedValueOnce([]);
    const mock = createMockRes();
    await handler(createMockReq({ method: 'GET', query: { deviceId: 'device-999999' } }), mock.res);
    expect(mock.statusCode).toBe(200);
    expect(mock.jsonBody).toEqual({ progress: [] });
  });

  it('maps repository failures to a 500 envelope', async () => {
    mockedGetProgress.mockRejectedValueOnce(new Error('boom'));
    const mock = createMockRes();
    await handler(createMockReq({ method: 'GET', query: { deviceId: 'device-123456' } }), mock.res);
    expect(mock.statusCode).toBe(500);
  });
});
