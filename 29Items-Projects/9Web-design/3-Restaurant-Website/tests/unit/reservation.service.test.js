import { describe, expect, it, vi } from 'vitest';
import { createReservationController } from '../../src/backend/controllers/reservationController.js';
import {
  createFormspreeNotifier,
  createReservationService
} from '../../src/backend/services/reservationService.js';
import { validReservation } from '../fixtures/reservations.js';

describe('reservation service', () => {
  it('validates filters before repository access', async () => {
    const repository = createRepository();
    const service = createReservationService({ repository, notifier: noopNotifier });

    await expect(service.listReservations({ status: 'unknown' })).rejects.toMatchObject({
      status: 400,
      code: 'validation_error'
    });
  });

  it('requires ids for single-record operations', async () => {
    const service = createReservationService({ repository: createRepository(), notifier: noopNotifier });

    await expect(service.getReservation('')).rejects.toMatchObject({ status: 400, code: 'missing_id' });
  });

  it('surfaces notification delivery failures after persistence', async () => {
    const service = createReservationService({
      repository: createRepository(),
      notifier: {
        notifyReservationCreated: async () => {
          throw new Error('mail unavailable');
        }
      }
    });

    await expect(service.createReservation(validReservation())).rejects.toThrow('mail unavailable');
  });

  it('caps page size and returns pagination metadata', async () => {
    const repository = createRepository();
    const service = createReservationService({ repository, notifier: noopNotifier });
    await service.createReservation(validReservation());

    const result = await service.listReservations({ page: '1', pageSize: '500' });

    expect(result.pagination).toMatchObject({ page: 1, pageSize: 100, total: 1, totalPages: 1 });
  });

  it('rejects invalid date filters', async () => {
    const service = createReservationService({ repository: createRepository(), notifier: noopNotifier });

    await expect(service.listReservations({ date: 'tomorrow' })).rejects.toMatchObject({
      status: 400,
      code: 'validation_error'
    });
  });

  it('sends Formspree notifications when configured', async () => {
    const fetchImpl = vi.fn(async () => ({ ok: true }));
    const notifier = createFormspreeNotifier({
      endpoint: 'https://formspree.io/f/live-form',
      fetchImpl
    });

    await notifier.notifyReservationCreated({
      ...validReservation(),
      status: 'requested'
    });

    expect(fetchImpl).toHaveBeenCalledWith(
      'https://formspree.io/f/live-form',
      expect.objectContaining({ method: 'POST' })
    );
  });

  it('maps Formspree rejection to a public service error', async () => {
    const notifier = createFormspreeNotifier({
      endpoint: 'https://formspree.io/f/live-form',
      fetchImpl: async () => ({ ok: false })
    });

    await expect(notifier.notifyReservationCreated(validReservation())).rejects.toMatchObject({
      status: 502,
      code: 'notification_failed'
    });
  });

  it('returns public controller errors for unsupported methods', async () => {
    const controller = createReservationController({
      service: createReservationService({ repository: createRepository(), notifier: noopNotifier })
    });

    const result = await controller.handle({ method: 'PATCH' });

    expect(result.statusCode).toBe(405);
    expect(result.body.code).toBe('method_not_allowed');
  });
});

const noopNotifier = { notifyReservationCreated: async () => undefined };

function createRepository() {
  const rows = new Map();

  return {
    async list() {
      return { reservations: Array.from(rows.values()), total: rows.size };
    },
    async findById(id) {
      return rows.get(id) || null;
    },
    async create(input) {
      const reservation = {
        id: '00000000-0000-0000-0000-000000000001',
        ...input,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString()
      };
      rows.set(reservation.id, reservation);
      return reservation;
    },
    async update(id, input) {
      const reservation = rows.get(id);
      if (!reservation) return null;
      const updated = { ...reservation, ...input, updatedAt: new Date().toISOString() };
      rows.set(id, updated);
      return updated;
    },
    async delete(id) {
      const reservation = rows.get(id);
      rows.delete(id);
      return reservation || null;
    }
  };
}
