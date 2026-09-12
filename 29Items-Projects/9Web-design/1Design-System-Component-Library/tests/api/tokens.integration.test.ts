/**
 * @jest-environment node
 */

import request from 'supertest';
import { createApp } from '../../src/api/app';
import { MemoryTokenRepository } from '../../src/db/token.repository';
import { TokenService } from '../../src/services/token.service';

async function createTestApp() {
  const service = new TokenService(new MemoryTokenRepository());
  const tokenSet = await service.createTokenSet({
    name: 'core',
    description: 'Core tokens',
    source: 'test'
  });
  return { app: createApp(service), tokenSet };
}

describe('token governance API', () => {
  it('runs the main token lifecycle over HTTP', async () => {
    const { app, tokenSet } = await createTestApp();

    const created = await request(app)
      .post('/api/tokens')
      .send({
        tokenSetId: tokenSet.id,
        name: 'color.brand.primary',
        category: 'color',
        value: '#265CFF',
        description: 'Primary'
      })
      .expect(201);

    const tokenId = created.body.data.id as string;
    await request(app).post(`/api/tokens/${tokenId}/approve`).send({ actor: 'qa' }).expect(200);
    await request(app).post(`/api/tokens/${tokenId}/approve`).send({ actor: 'qa' }).expect(200);
    const versions = await request(app).get(`/api/tokens/${tokenId}/versions`).expect(200);
    const audit = await request(app).get(`/api/tokens/${tokenId}/audit`).expect(200);
    const status = await request(app).get('/api/status').expect(200);

    expect(versions.body.data).toHaveLength(2);
    expect(audit.body.data.length).toBeGreaterThan(0);
    expect(status.body.counts.approved).toBe(1);

    await request(app)
      .patch(`/api/tokens/${tokenId}`)
      .send({ value: '#1745C7', changeNote: 'Contrast update' })
      .expect(200);
    await request(app).post(`/api/tokens/${tokenId}/reject`).send({ actor: 'qa' }).expect(200);
    await request(app).post(`/api/tokens/${tokenId}/deprecate`).send({ actor: 'qa' }).expect(200);
    await request(app).post(`/api/tokens/${tokenId}/reject`).send({ actor: 'qa' }).expect(409);
    await request(app).post(`/api/tokens/${tokenId}/approve`).send({ actor: 42 }).expect(400);

    const list = await request(app).get(`/api/tokens?tokenSetId=${tokenSet.id}`).expect(200);
    expect(list.body.data[0].value).toBe('#1745C7');

    await request(app).delete(`/api/tokens/${tokenId}`).expect(204);
    await request(app).get(`/api/tokens/${tokenId}`).expect(404);
    await request(app).get(`/api/tokens/${tokenId}/versions`).expect(404);
  });

  it('supports token set CRUD, diff, changelog, and validation errors', async () => {
    const { app, tokenSet } = await createTestApp();

    const nextSet = await request(app)
      .post('/api/token-sets')
      .send({ name: 'next', description: 'Next release', source: 'test' })
      .expect(201);

    await request(app)
      .post('/api/tokens')
      .send({
        tokenSetId: tokenSet.id,
        name: 'spacing.component',
        category: 'spacing',
        value: '1rem'
      })
      .expect(201);
    await request(app)
      .post('/api/tokens')
      .send({
        tokenSetId: nextSet.body.data.id,
        name: 'spacing.component',
        category: 'spacing',
        value: '2rem'
      })
      .expect(201);

    const diff = await request(app)
      .get(`/api/token-sets/${tokenSet.id}/diff/${nextSet.body.data.id}`)
      .expect(200);
    expect(diff.body.data[0].changeType).toBe('changed');

    await request(app).post('/api/tokens').send({ name: 'bad' }).expect(400);
    await request(app).get(`/api/tokens/changelog?tokenSetId=${tokenSet.id}`).expect(200);
    await request(app).patch('/api/tokens/not-a-token').send({ value: '1rem' }).expect(404);
    await request(app)
      .patch(`/api/token-sets/${nextSet.body.data.id}`)
      .send({ description: 'Updated' })
      .expect(200);
    await request(app).get(`/api/token-sets/${nextSet.body.data.id}`).expect(200);
    await request(app).get(`/api/token-sets/${nextSet.body.data.id}/audit`).expect(200);
    await request(app).patch(`/api/token-sets/${nextSet.body.data.id}`).send({}).expect(400);
    await request(app).delete(`/api/token-sets/${nextSet.body.data.id}`).expect(204);
  });
});
