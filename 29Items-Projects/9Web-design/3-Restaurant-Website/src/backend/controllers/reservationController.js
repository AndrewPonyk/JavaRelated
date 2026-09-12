import { AppError, toPublicError } from '../errors.js';
import { logger } from '../logger.js';
import { createReservationService } from '../services/reservationService.js';

export function createReservationController({ service = createReservationService() } = {}) {
  return {
    async handle({ method, id, query = {}, body = {} }) {
      try {
        if (method === 'GET') {
          const result = id
            ? await service.getReservation(id)
            : await service.listReservations(pickFilters(query));
          return response(200, result);
        }

        if (method === 'POST') {
          const result = await service.createReservation(body);
          return response(201, result);
        }

        if (method === 'PUT') {
          const result = await service.updateReservation(id, body);
          return response(200, result);
        }

        if (method === 'DELETE') {
          const result = await service.deleteReservation(id);
          return response(200, result);
        }

        throw new AppError('Method not allowed.', { status: 405, code: 'method_not_allowed' });
      } catch (error) {
        logError(error, method);
        return response(error.status || 500, toPublicError(error));
      }
    }
  };
}

export function sendExpressResult(res, result) {
  res.status(result.statusCode).json(result.body);
}

function pickFilters(query) {
  return {
    status: query.status || undefined,
    date: query.date || undefined,
    page: query.page || undefined,
    pageSize: query.pageSize || undefined
  };
}

function response(statusCode, body) {
  return { statusCode, body };
}

function logError(error, method) {
  if (error instanceof AppError && error.status < 500) return;

  logger.error('reservation_api_error', {
    method,
    message: error.message,
    code: error.code || 'internal_error'
  });
}
