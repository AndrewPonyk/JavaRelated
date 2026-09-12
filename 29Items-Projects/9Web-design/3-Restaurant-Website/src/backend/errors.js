export class AppError extends Error {
  constructor(message, { status = 500, code = 'internal_error', details = [] } = {}) {
    super(message);
    this.name = 'AppError';
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

export function toPublicError(error) {
  if (error instanceof AppError) {
    return {
      ok: false,
      error: error.message,
      code: error.code,
      details: error.details
    };
  }

  return {
    ok: false,
    error: 'Reservation service is unavailable.',
    code: 'internal_error',
    details: []
  };
}
