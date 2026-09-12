/**
 * @jest-environment node
 */

import request from 'supertest';
import { createApp } from '../../src/api/app';
import { MemoryTokenRepository } from '../../src/db/token.repository';
import { TokenService } from '../../src/services/token.service';

const originalEnv = { ...process.env };

async function createTestApp() {
  const service = new TokenService(new MemoryTokenRepository());
  const tokenSet = await service.createTokenSet({ name: 'core', source: 'test' });
  const app = createApp(service);
  return { app, tokenSet };
}

describe('production hardening', () => {
  afterEach(() => {
    process.env = { ...originalEnv };
  });

  it('returns pagination metadata and rejects invalid query values', async () => {
    const { app, tokenSet } = await createTestApp();

    await request(app)
      .post('/api/tokens')
      .send({
        tokenSetId: tokenSet.id,
        name: 'color.brand.primary',
        category: 'color',
        value: '#265CFF'
      })
      .expect(201);

    const list = await request(app).get('/api/tokens?limit=1&offset=0&category=color').expect(200);
    expect(list.body.meta).toEqual({ limit: 1, offset: 0, total: 1 });

    await request(app).get('/api/tokens?category=invalid').expect(400);
    await request(app).get('/api/tokens?limit=101').expect(400);
    await request(app).get('/api/token-sets?offset=-1').expect(400);
  });

  it('enforces optional API keys and JSON mutation content types', async () => {
    process.env.API_KEY = 'test-key';
    const { app } = await createTestApp();

    await request(app).get('/health').expect(200);
    await request(app).get('/api/status').expect(401);
    await request(app).get('/api/status').set('x-api-key', 'test-key').expect(200);
    await request(app)
      .post('/api/token-sets')
      .set('x-api-key', 'test-key')
      .set('content-type', 'text/plain')
      .send('not-json')
      .expect(415);
  });

  it('rejects non-HTTPS requests when HTTPS enforcement is enabled', async () => {
    process.env.REQUIRE_HTTPS = 'true';
    const { app } = await createTestApp();

    await request(app).get('/health').expect(400);
    await request(app).get('/health').set('x-forwarded-proto', 'https').expect(200);
  });
});
