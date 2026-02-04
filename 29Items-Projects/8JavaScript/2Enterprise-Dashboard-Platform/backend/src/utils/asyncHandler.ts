import { Request, Response, NextFunction } from 'express';

// Async handler wrapper to catch errors in async route handlers
export const asyncHandler = <T extends Request = Request>(
  fn: (req: T, res: Response, next: NextFunction) => Promise<void | Response>
) => {
  return (req: T, res: Response, next: NextFunction): void => {
    Promise.resolve(fn(req, res, next)).catch(next);
  };
};