import { newDb } from 'pg-mem';
import request from 'supertest';
import { readFile } from 'node:fs/promises';
import { beforeEach, describe, expect, it } from 'vitest';
import { createApp } from '../../src/backend/app.js';
import { createReservationRepository } from '../../src/backend/repositories/reservationRepository.js';
import { createReservationService } from '../../src/backend/services/reservationService.js';
import { validReservation } from '../fixtures/reservations.js';

let app;

beforeEach(async () => {
  const db = newDb();
  const { Pool } = db.adapters.createPg();
  const pool = new Pool();
  await pool.query(await readFile('migrations/001_create_reservations.sql', 'utf8'));

  const repository = createReservationRepository(pool);
  const service = createReservationService({
    repository,
    notifier: { notifyReservationCreated: async () => undefined }
  });

  app = createApp({ service, serveStatic: false });
});

describe('reservation API', () => {
  it('creates, lists, reads, updates, and deletes a reservation', async () => {
    const created = await request(app).post('/api/reservations').send(validReservation()).expect(201);

    expect(created.body.ok).toBe(true);
    expect(created.body.reservation.id).toEqual(expect.any(String));
    expect(created.body.reservation.status).toBe('requested');

    const id = created.body.reservation.id;

    const list = await request(app).get('/api/reservations?status=requested').expect(200);
    expect(list.body.reservations).toHaveLength(1);
    expect(list.body.pagination).toEqual({
      page: 1,
      pageSize: 20,
      total: 1,
      totalPages: 1
    });

    const read = await request(app).get(`/api/reservations/${id}`).expect(200);
    expect(read.body.reservation.email).toBe('ada@example.com');

    const updated = await request(app)
      .put(`/api/reservations/${id}`)
      .send({ status: 'confirmed', notes: 'Confirmed by phone' })
      .expect(200);
    expect(updated.body.reservation.status).toBe('confirmed');

    const deleted = await request(app).delete(`/api/reservations/${id}`).expect(200);
    expect(deleted.body.reservation.id).toBe(id);

    const empty = await request(app).get('/api/reservations').expect(200);
    expect(empty.body.reservations).toHaveLength(0);
  });

  it('returns validation errors for invalid reservations', async () => {
    const response = await request(app)
      .post('/api/reservations')
      .send(validReservation({ email: 'bad-email', partySize: 20 }))
      .expect(400);

    expect(response.body.ok).toBe(false);
    expect(response.body.code).toBe('validation_error');
    expect(response.body.details.length).toBeGreaterThan(0);
  });

  it('rejects invalid filters and unsupported content types', async () => {
    await request(app).get('/api/reservations?page=0').expect(400);
    await request(app)
      .post('/api/reservations')
      .set('Content-Type', 'text/plain')
      .send('not-json')
      .expect(415);
  });

  it('returns not found for unknown reservation ids', async () => {
    const response = await request(app)
      .get('/api/reservations/00000000-0000-0000-0000-000000000000')
      .expect(404);

    expect(response.body.code).toBe('not_found');
  });
});
