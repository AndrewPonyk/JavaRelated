import { Request, Response, NextFunction } from 'express';
import { z, ZodError, ZodSchema } from 'zod';
import { ValidationError } from '@/utils/errors.js';
import { logger } from '@/utils/logger.js';

// Validation target types
type ValidationTarget = 'body' | 'query' | 'params' | 'headers';

// Validation configuration
interface ValidationConfig {
  body?: ZodSchema;
  query?: ZodSchema;
  params?: ZodSchema;
  headers?: ZodSchema;
  skipOnError?: boolean; // Continue to next middleware even on validation error
  logValidationErrors?: boolean;
}

// Create validation middleware
export const validate = (config: ValidationConfig) => {
  return (req: Request, res: Response, next: NextFunction): void => {
    const errors: Array<{ target: ValidationTarget; issues: any[] }> = [];

    try {
      // Validate body
      if (config.body) {
        try {
          req.body = config.body.parse(req.body);
        } catch (error) {
          if (error instanceof ZodError) {
            errors.push({
              target: 'body',
              issues: error.issues,
            });
          }
        }
      }

      // Validate query parameters
      if (config.query) {
        try {
          req.query = config.query.parse(req.query);
        } catch (error) {
          if (error instanceof ZodError) {
            errors.push({
              target: 'query',
              issues: error.issues,
            });
          }
        }
      }

      // Validate URL parameters
      if (config.params) {
        try {
          req.params = config.params.parse(req.params);
        } catch (error) {
          if (error instanceof ZodError) {
            errors.push({
              target: 'params',
              issues: error.issues,
            });
          }
        }
      }

      // Validate headers
      if (config.headers) {
        try {
          const headerValidation = config.headers.parse(req.headers);
          // Don't override req.headers, just validate
        } catch (error) {
          if (error instanceof ZodError) {
            errors.push({
              target: 'headers',
              issues: error.issues,
            });
          }
        }
      }

      // Handle validation errors
      if (errors.length > 0) {
        if (config.logValidationErrors) {
          logger.warn('Request validation failed', {
            requestId: (req as any).requestId,
            method: req.method,
            url: req.url,
            errors,
            timestamp: new Date().toISOString(),
          });
        }

        if (config.skipOnError) {
          // Add validation errors to request for later handling
          (req as any).validationErrors = errors;
          return next();
        }

        // Format validation errors for response
        const formattedErrors = errors.flatMap(error =>
          error.issues.map(issue => ({
            target: error.target,
            path: issue.path.join('.'),
            message: issue.message,
            code: issue.code,
            received: issue.received,
          }))
        );

        throw new ValidationError('Request validation failed', {
          errors: formattedErrors,
          totalErrors: formattedErrors.length,
        });
      }

      next();
    } catch (error) {
      next(error);
    }
  };
};

// Shorthand validation middlewares for common cases
export const validateBody = (schema: ZodSchema, options?: { skipOnError?: boolean; logErrors?: boolean }) => {
  return validate({
    body: schema,
    skipOnError: options?.skipOnError,
    logValidationErrors: options?.logErrors,
  });
};

export const validateQuery = (schema: ZodSchema, options?: { skipOnError?: boolean; logErrors?: boolean }) => {
  return validate({
    query: schema,
    skipOnError: options?.skipOnError,
    logValidationErrors: options?.logErrors,
  });
};

export const validateParams = (schema: ZodSchema, options?: { skipOnError?: boolean; logErrors?: boolean }) => {
  return validate({
    params: schema,
    skipOnError: options?.skipOnError,
    logValidationErrors: options?.logErrors,
  });
};

export const validateHeaders = (schema: ZodSchema, options?: { skipOnError?: boolean; logErrors?: boolean }) => {
  return validate({
    headers: schema,
    skipOnError: options?.skipOnError,
    logValidationErrors: options?.logErrors,
  });
};

// Common validation schemas
export const commonSchemas = {
  // UUID validation for IDs
  uuidParam: z.object({
    id: z.string().uuid('Invalid ID format'),
  }),

  // Pagination query parameters
  pagination: z.object({
    page: z.string().optional().transform(val => val ? parseInt(val) : 1),
    limit: z.string().optional().transform(val => val ? parseInt(val) : 10),
    sortBy: z.string().optional(),
    sortOrder: z.enum(['asc', 'desc']).optional().default('desc'),
  }),

  // Search query parameters
  search: z.object({
    q: z.string().min(1, 'Search query is required'),
    limit: z.string().optional().transform(val => val ? parseInt(val) : 10),
  }),

  // Date range validation
  dateRange: z.object({
    startDate: z.string().datetime().optional(),
    endDate: z.string().datetime().optional(),
  }).refine(
    (data) => {
      if (data.startDate && data.endDate) {
        return new Date(data.startDate) <= new Date(data.endDate);
      }
      return true;
    },
    {
      message: 'Start date must be before end date',
    }
  ),

  // File upload validation
  fileUpload: z.object({
    filename: z.string().min(1),
    mimetype: z.string().regex(/^[a-zA-Z]+\/[a-zA-Z0-9\-\+\.]+$/),
    size: z.number().positive().max(10 * 1024 * 1024), // 10MB max
  }),

  // Email validation
  email: z.object({
    email: z.string().email('Invalid email format'),
  }),

  // Password validation
  password: z.object({
    password: z.string()
      .min(8, 'Password must be at least 8 characters long')
      .regex(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/, 'Password must contain at least one lowercase letter, one uppercase letter, and one number'),
  }),

  // Boolean query parameter
  booleanQuery: z.object({
    value: z.string().optional().transform(val => {
      if (val === 'true') return true;
      if (val === 'false') return false;
      return undefined;
    }),
  }),
};

// Validation error formatter
export const formatValidationError = (error: ZodError): any => {
  return {
    code: 'VALIDATION_ERROR',
    message: 'Request validation failed',
    details: {
      issues: error.issues.map(issue => ({
        path: issue.path.join('.'),
        message: issue.message,
        code: issue.code,
        received: (issue as any).received,
      })),
      totalErrors: error.issues.length,
    },
  };
};

// Custom validation helpers
export const validateEnum = <T extends string>(values: readonly T[], message?: string) => {
  return z.enum(values as [T, ...T[]], {
    errorMap: () => ({ message: message || `Value must be one of: ${values.join(', ')}` }),
  });
};

export const validateOptionalEnum = <T extends string>(values: readonly T[], message?: string) => {
  return validateEnum(values, message).optional();
};

// Conditional validation based on other fields
export const conditionalValidation = <T>(
  condition: (data: any) => boolean,
  schema: ZodSchema<T>
) => {
  return z.any().superRefine((data, ctx) => {
    if (condition(data)) {
      const result = schema.safeParse(data);
      if (!result.success) {
        result.error.issues.forEach(issue => {
          ctx.addIssue(issue);
        });
      }
    }
  });
};

// Transform and validate middleware for complex data types
export const transformAndValidate = <T>(
  transformer: (data: any) => any,
  validator: ZodSchema<T>
) => {
  return (req: Request, res: Response, next: NextFunction): void => {
    try {
      // Transform the data
      const transformed = transformer(req.body);

      // Validate the transformed data
      req.body = validator.parse(transformed);

      next();
    } catch (error) {
      if (error instanceof ZodError) {
        next(new ValidationError('Data transformation and validation failed', {
          errors: error.issues,
        }));
      } else {
        next(error);
      }
    }
  };
};

// Sanitization helpers
export const sanitizeHtml = z.string().transform(val => {
  // TODO: Implement HTML sanitization (could use DOMPurify or similar)
  return val.replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, '');
});

export const sanitizeString = z.string().transform(val => {
  return val.trim().replace(/\s+/g, ' '); // Normalize whitespace
});

// Rate limiting validation
export const validateRateLimit = (
  maxRequests: number,
  windowMs: number,
  keyGenerator?: (req: Request) => string
) => {
  const requests = new Map<string, { count: number; resetTime: number }>();

  return (req: Request, res: Response, next: NextFunction): void => {
    const key = keyGenerator ? keyGenerator(req) : req.ip || 'unknown';
    const now = Date.now();
    const windowStart = Math.floor(now / windowMs) * windowMs;

    let record = requests.get(key);

    // Reset if window has passed
    if (!record || record.resetTime <= now) {
      record = { count: 0, resetTime: windowStart + windowMs };
      requests.set(key, record);
    }

    record.count++;

    // Set rate limit headers
    res.setHeader('X-RateLimit-Limit', maxRequests);
    res.setHeader('X-RateLimit-Remaining', Math.max(0, maxRequests - record.count));
    res.setHeader('X-RateLimit-Reset', Math.ceil(record.resetTime / 1000));

    if (record.count > maxRequests) {
      const error = new ValidationError('Rate limit exceeded', {
        limit: maxRequests,
        windowMs,
        retryAfter: Math.ceil((record.resetTime - now) / 1000),
      });

      return next(error);
    }

    next();
  };
};

export default {
  validate,
  validateBody,
  validateQuery,
  validateParams,
  validateHeaders,
  commonSchemas,
  formatValidationError,
  validateEnum,
  validateOptionalEnum,
  conditionalValidation,
  transformAndValidate,
  sanitizeHtml,
  sanitizeString,
  validateRateLimit,
};