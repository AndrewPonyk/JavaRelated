import { createReservationRepository } from '../repositories/reservationRepository.js';
import { getPool } from '../db/pool.js';
import { AppError } from '../errors.js';
import {
  RESERVATION_STATUSES,
  validateReservationInput,
  validateReservationUpdate
} from '../../shared/validation/reservation.js';

export function createReservationService({
  repository = createReservationRepository(getPool()),
  notifier = createFormspreeNotifier()
} = {}) {
  return {
    async listReservations(filters = {}) {
      const pagination = validateFilters(filters);
      const { reservations, total } = await repository.list({ ...filters, ...pagination });
      return {
        ok: true,
        reservations,
        pagination: {
          page: pagination.page,
          pageSize: pagination.pageSize,
          total,
          totalPages: Math.ceil(total / pagination.pageSize)
        }
      };
    },

    async getReservation(id) {
      const reservation = await repository.findById(requireId(id));
      if (!reservation) throw notFound(id);
      return { ok: true, reservation };
    },

    async createReservation(input) {
      const validation = validateReservationInput(input);
      if (!validation.ok) throw validationError(validation.errors);

      const reservation = await repository.create(validation.data);
      await notifier.notifyReservationCreated(reservation);

      return { ok: true, reservation };
    },

    async updateReservation(id, input) {
      const validation = validateReservationUpdate(input);
      if (!validation.ok) throw validationError(validation.errors);

      const reservation = await repository.update(requireId(id), validation.data);
      if (!reservation) throw notFound(id);

      return { ok: true, reservation };
    },

    async deleteReservation(id) {
      const reservation = await repository.delete(requireId(id));
      if (!reservation) throw notFound(id);

      return { ok: true, reservation };
    }
  };
}

let defaultService;

export const listReservations = (filters) => getDefaultService().listReservations(filters);
export const getReservation = (id) => getDefaultService().getReservation(id);
export const createReservation = (input) => getDefaultService().createReservation(input);
export const updateReservation = (id, input) => getDefaultService().updateReservation(id, input);
export const deleteReservation = (id) => getDefaultService().deleteReservation(id);

export function createFormspreeNotifier({ endpoint = process.env.FORM_ENDPOINT, fetchImpl = fetch } = {}) {
  const enabled = Boolean(endpoint) && !endpoint.includes('your-form-id');

  return {
    async notifyReservationCreated(reservation) {
      if (!enabled) return;

      const response = await fetchImpl(endpoint, {
        method: 'POST',
        headers: {
          Accept: 'application/json',
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          name: reservation.name,
          email: reservation.email,
          date: reservation.date,
          partySize: reservation.partySize,
          notes: reservation.notes,
          status: reservation.status
        })
      });

      if (!response.ok) {
        throw new AppError('Reservation was saved, but notification delivery failed.', {
          status: 502,
          code: 'notification_failed'
        });
      }
    }
  };
}

function validateFilters(filters) {
  if (filters.status && !RESERVATION_STATUSES.includes(filters.status)) {
    throw new AppError('Reservation status filter is invalid.', {
      status: 400,
      code: 'validation_error',
      details: [{ field: 'status', message: 'Reservation status filter is invalid.' }]
    });
  }

  if (filters.date && !isValidDate(filters.date)) {
    throw new AppError('Reservation date filter is invalid.', {
      status: 400,
      code: 'validation_error',
      details: [{ field: 'date', message: 'Reservation date filter is invalid.' }]
    });
  }

  const page = parsePositiveInteger(filters.page, 1);
  const pageSize = Math.min(parsePositiveInteger(filters.pageSize, 20), 100);

  return {
    page,
    pageSize,
    offset: (page - 1) * pageSize
  };
}

function validationError(details) {
  return new AppError(details[0]?.message || 'Reservation input is invalid.', {
    status: 400,
    code: 'validation_error',
    details
  });
}

function requireId(id) {
  const value = String(id || '').trim();
  if (!value) {
    throw new AppError('Reservation id is required.', {
      status: 400,
      code: 'missing_id',
      details: [{ field: 'id', message: 'Reservation id is required.' }]
    });
  }
  return value;
}

function notFound(id) {
  return new AppError(`Reservation ${id} was not found.`, {
    status: 404,
    code: 'not_found'
  });
}

function getDefaultService() {
  if (!defaultService) {
    defaultService = createReservationService();
  }
  return defaultService;
}

function parsePositiveInteger(value, fallback) {
  if (value === undefined || value === null || value === '') return fallback;

  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < 1) {
    throw new AppError('Pagination parameters must be positive integers.', {
      status: 400,
      code: 'validation_error',
      details: [{ field: 'pagination', message: 'Pagination parameters must be positive integers.' }]
    });
  }

  return parsed;
}

function isValidDate(value) {
  const parsed = new Date(`${value}T00:00:00`);
  return /^\d{4}-\d{2}-\d{2}$/.test(String(value)) && !Number.isNaN(parsed.getTime());
}
