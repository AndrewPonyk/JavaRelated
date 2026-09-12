// @vitest-environment node
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../../api/_lib/progressRepo', () => ({
  recordAttempt: vi.fn(),
}));

import { recordAttempt } from '../../../api/_lib/progressRepo';
import { resetRateLimiter } from '../../../api/_lib/rateLimit';
import handler from '../../../api/attempts';
import { createMockReq, createMockRes } from './mocks';

const mockedRecordAttempt = vi.mocked(recordAttempt);

const validBody = {
  attemptId: '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d',
  deviceId: 'device-123456',
  topic: 'eigenvalues',
  difficulty: 3,
  seed: 42,
  correct: true,
  durationMs: 5000,
};

const confirmedRow = { topic: 'eigenvalues' as const, mastery: 0.15, attempts: 1 };

describe('POST /api/attempts', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    resetRateLimiter();
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  it('responds 405 to GET with an Allow header', async () => {
    const mock = createMockRes();
    await handler(createMockReq({ method: 'GET' }), mock.res);
    expect(mock.statusCode).toBe(405);
    expect(mock.headers.Allow).toBe('POST');
  });

  it('responds 400 with field detail on invalid bodies', async () => {
    const mock = createMockRes();
    await handler(createMockReq({ method: 'POST', body: { deviceId: 'x' } }), mock.res);
    expect(mock.statusCode).toBe(400);
    const body = mock.jsonBody as { error: { code: string; message: string } };
    expect(body.error.code).toBe('VALIDATION_ERROR');
    expect(body.error.message).toContain('deviceId');
    expect(mockedRecordAttempt).not.toHaveBeenCalled();
  });

  it('rejects a non-UUID attemptId (idempotency key is mandatory)', async () => {
    const mock = createMockRes();
    await handler(
      createMockReq({ method: 'POST', body: { ...validBody, attemptId: 'not-a-uuid' } }),
      mock.res,
    );
    expect(mock.statusCode).toBe(400);
    expect((mock.jsonBody as { error: { message: string } }).error.message).toContain('attemptId');
  });

  it('records a valid attempt and returns the server-computed progress row', async () => {
    mockedRecordAttempt.mockResolvedValueOnce(confirmedRow);
    const mock = createMockRes();
    await handler(createMockReq({ method: 'POST', body: validBody }), mock.res);

    expect(mock.statusCode).toBe(201);
    expect(mock.jsonBody).toEqual({ ok: true, progress: confirmedRow });
    expect(mockedRecordAttempt).toHaveBeenCalledWith(validBody);
  });

  it('rate-limits a hammering client with 429', async () => {
    mockedRecordAttempt.mockResolvedValue(confirmedRow);
    const req = () =>
      createMockReq({
        method: 'POST',
        body: validBody,
        headers: { 'x-forwarded-for': '10.0.0.1' },
      });

    let lastStatus: number | null = null;
    for (let i = 0; i < 31; i++) {
      const mock = createMockRes();
      await handler(req(), mock.res);
      lastStatus = mock.statusCode;
    }
    expect(lastStatus).toBe(429);
    expect(mockedRecordAttempt).toHaveBeenCalledTimes(30);
  });

  it('maps repository failures to a 500 envelope without leaking internals', async () => {
    mockedRecordAttempt.mockRejectedValueOnce(new Error('db exploded: secret host'));
    const mock = createMockRes();
    await handler(createMockReq({ method: 'POST', body: validBody }), mock.res);

    expect(mock.statusCode).toBe(500);
    const body = mock.jsonBody as { error: { code: string; message: string } };
    expect(body.error.code).toBe('INTERNAL');
    expect(body.error.message).not.toContain('secret host');
  });
});
