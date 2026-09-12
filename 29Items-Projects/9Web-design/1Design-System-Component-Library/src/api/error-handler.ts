import type { ErrorRequestHandler } from 'express';
import { isDomainError } from '../services/domain-errors';

export const errorHandler: ErrorRequestHandler = (error, _request, response, next) => {
  void next;

  if (isDomainError(error)) {
    response.status(error.statusCode).json({
      error: {
        code: error.code,
        message: error.message,
        details: error.details
      }
    });
    return;
  }

  response.status(500).json({
    error: {
      code: 'INTERNAL_SERVER_ERROR',
      message: process.env.NODE_ENV === 'production' ? 'Unexpected server error' : error.message
    }
  });
};
