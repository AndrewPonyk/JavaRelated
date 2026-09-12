import express from 'express';
import request from 'supertest';

import { createApp } from '../src/server';
import { ApiError, errorMiddleware } from '../src/middleware/error.middleware';

describe('HTTP API foundation', () => {
  it('serves the health endpoint', async () => {
    const response = await request(createApp()).get('/health').expect(200);
    expect(response.body).toEqual({ status: 'ok' });
  });

  it('returns stable validation errors before controller work runs', async () => {
    const response = await request(createApp()).get('/api/pois?venueId=invalid').expect(400);

    expect(response.body.error).toMatchObject({
      code: 'validation_failed',
      message: 'Request validation failed.',
    });
  });

  it('translates domain errors and database conflicts to API errors', async () => {
    const app = express();
    app.get('/domain-error', () => {
      throw new ApiError(404, 'missing', 'Missing resource.');
    });
    app.get('/conflict', () => {
      throw { code: '23505' };
    });
    app.use(errorMiddleware);

    await request(app)
      .get('/domain-error')
      .expect(404, {
        error: {
          code: 'missing',
          message: 'Missing resource.',
        },
      });

    const response = await request(app).get('/conflict').expect(409);
    expect(response.body.error.code).toBe('conflict');
  });

  it('rejects plain HTTP when HTTPS enforcement is enabled', async () => {
    const previous = process.env.REQUIRE_HTTPS;
    process.env.REQUIRE_HTTPS = 'true';

    try {
      const response = await request(createApp()).get('/health').expect(403);
      expect(response.body.error).toMatchObject({
        code: 'https_required',
        message: 'HTTPS is required.',
      });
    } finally {
      if (previous == null) {
        delete process.env.REQUIRE_HTTPS;
      } else {
        process.env.REQUIRE_HTTPS = previous;
      }
    }
  });
});
