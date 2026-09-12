import type { NextFunction, Request, RequestHandler, Response } from 'express';

export function requireHttps(): RequestHandler {
  return (request, response, next) => {
    if (process.env.REQUIRE_HTTPS !== 'true') {
      next();
      return;
    }

    const forwardedProtocol = request.header('x-forwarded-proto');
    if (request.secure || forwardedProtocol === 'https') {
      next();
      return;
    }

    response.status(400).json({
      error: {
        code: 'HTTPS_REQUIRED',
        message: 'HTTPS is required for this endpoint'
      }
    });
  };
}

export function requireApiKey(): RequestHandler {
  return (request: Request, response: Response, next: NextFunction) => {
    if (!request.path.startsWith('/api')) {
      next();
      return;
    }

    const expectedApiKey = process.env.API_KEY;
    if (!expectedApiKey) {
      next();
      return;
    }

    if (request.header('x-api-key') === expectedApiKey) {
      next();
      return;
    }

    response.status(401).json({
      error: {
        code: 'UNAUTHORIZED',
        message: 'A valid API key is required'
      }
    });
  };
}

export function requireJsonMutations(): RequestHandler {
  return (request, response, next) => {
    if (
      !request.path.startsWith('/api') ||
      !['POST', 'PUT', 'PATCH'].includes(request.method) ||
      request.is('application/json')
    ) {
      next();
      return;
    }

    response.status(415).json({
      error: {
        code: 'UNSUPPORTED_MEDIA_TYPE',
        message: 'Mutation requests must use application/json'
      }
    });
  };
}

export function getCorsOrigin(): string | string[] | boolean {
  const allowedOrigins = process.env.CORS_ORIGIN;
  if (!allowedOrigins || allowedOrigins === '*') {
    return true;
  }

  return allowedOrigins
    .split(',')
    .map((origin) => origin.trim())
    .filter(Boolean);
}
