import type { NextFunction, Request, Response } from 'express';
import type { ZodSchema } from 'zod';

import { ApiError } from './error.middleware';

type RequestPart = 'body' | 'query' | 'params';

export function validate(part: RequestPart, schema: ZodSchema) {
  return (req: Request, _res: Response, next: NextFunction) => {
    const result = schema.safeParse(req[part]);

    if (!result.success) {
      next(
        new ApiError(
          400,
          'validation_failed',
          'Request validation failed.',
          result.error.flatten(),
        ),
      );
      return;
    }

    req[part] = result.data;
    next();
  };
}
