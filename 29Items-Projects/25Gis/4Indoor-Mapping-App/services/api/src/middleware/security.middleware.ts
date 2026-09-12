import type { NextFunction, Request, Response } from 'express';

import { ApiError } from './error.middleware';

export function requireHttps(req: Request, _res: Response, next: NextFunction) {
  if (process.env.REQUIRE_HTTPS !== 'true') {
    next();
    return;
  }

  const forwardedProto = req.header('x-forwarded-proto');
  if (req.secure || forwardedProto === 'https') {
    next();
    return;
  }

  next(new ApiError(403, 'https_required', 'HTTPS is required.'));
}
