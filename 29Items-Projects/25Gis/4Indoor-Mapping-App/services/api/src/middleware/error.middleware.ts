import type { ErrorRequestHandler } from 'express';
import pino from 'pino';

const logger = pino({ level: process.env.LOG_LEVEL ?? 'info' });

export class ApiError extends Error {
  constructor(
    public readonly statusCode: number,
    public readonly code: string,
    message: string,
    public readonly details?: unknown,
  ) {
    super(message);
  }
}

function isPgError(error: unknown): error is { code: string; detail?: string } {
  return typeof error === 'object' && error !== null && 'code' in error;
}

export const errorMiddleware: ErrorRequestHandler = (err, _req, res, _next) => {
  void _next;

  if (err instanceof ApiError) {
    res.status(err.statusCode).json({
      error: {
        code: err.code,
        message: err.message,
        details: err.details,
      },
    });
    return;
  }

  if (isPgError(err)) {
    if (err.code === '23505') {
      res.status(409).json({
        error: {
          code: 'conflict',
          message: 'A resource with these unique values already exists.',
        },
      });
      return;
    }

    if (err.code === '23503') {
      res.status(400).json({
        error: {
          code: 'invalid_reference',
          message: 'A referenced resource does not exist.',
        },
      });
      return;
    }

    if (err.code === '23514' || err.code === '22P02' || err.code === 'XX000') {
      res.status(400).json({
        error: {
          code: 'invalid_database_value',
          message: 'The request contains a value that cannot be stored.',
        },
      });
      return;
    }
  }

  logger.error({ err }, 'unhandled request error');
  res.status(500).json({
    error: {
      code: 'internal_error',
      message: 'An unexpected error occurred.',
    },
  });
};
