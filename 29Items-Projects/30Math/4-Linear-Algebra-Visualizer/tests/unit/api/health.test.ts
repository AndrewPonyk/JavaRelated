// @vitest-environment node
import { describe, expect, it } from 'vitest';

import handler from '../../../api/health';
import { createMockReq, createMockRes } from './mocks';

describe('GET /api/health', () => {
  it('reports liveness without touching any dependency', () => {
    const mock = createMockRes();
    handler(createMockReq(), mock.res);

    expect(mock.statusCode).toBe(200);
    const body = mock.jsonBody as { ok: boolean; service: string; timestamp: string };
    expect(body.ok).toBe(true);
    expect(body.service).toBe('linear-algebra-visualizer-api');
    expect(Number.isNaN(Date.parse(body.timestamp))).toBe(false);
  });
});
