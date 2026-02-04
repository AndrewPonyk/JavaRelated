import { z, ZodError } from 'zod';
import { ValidationError } from '@/utils/errors.js';
import { logger } from '@/utils/logger.js';
export const validate = (config) => {
    return (req, res, next) => {
        const errors = [];
        try {
            if (config.body) {
                try {
                    req.body = config.body.parse(req.body);
                }
                catch (error) {
                    if (error instanceof ZodError) {
                        errors.push({
                            target: 'body',
                            issues: error.issues,
                        });
                    }
                }
            }
            if (config.query) {
                try {
                    req.query = config.query.parse(req.query);
                }
                catch (error) {
                    if (error instanceof ZodError) {
                        errors.push({
                            target: 'query',
                            issues: error.issues,
                        });
                    }
                }
            }
            if (config.params) {
                try {
                    req.params = config.params.parse(req.params);
                }
                catch (error) {
                    if (error instanceof ZodError) {
                        errors.push({
                            target: 'params',
                            issues: error.issues,
                        });
                    }
                }
            }
            if (config.headers) {
                try {
                    const headerValidation = config.headers.parse(req.headers);
                }
                catch (error) {
                    if (error instanceof ZodError) {
                        errors.push({
                            target: 'headers',
                            issues: error.issues,
                        });
                    }
                }
            }
            if (errors.length > 0) {
                if (config.logValidationErrors) {
                    logger.warn('Request validation failed', {
                        requestId: req.requestId,
                        method: req.method,
                        url: req.url,
                        errors,
                        timestamp: new Date().toISOString(),
                    });
                }
                if (config.skipOnError) {
                    req.validationErrors = errors;
                    return next();
                }
                const formattedErrors = errors.flatMap(error => error.issues.map(issue => ({
                    target: error.target,
                    path: issue.path.join('.'),
                    message: issue.message,
                    code: issue.code,
                    received: issue.received,
                })));
                throw new ValidationError('Request validation failed', {
                    errors: formattedErrors,
                    totalErrors: formattedErrors.length,
                });
            }
            next();
        }
        catch (error) {
            next(error);
        }
    };
};
export const validateBody = (schema, options) => {
    return validate({
        body: schema,
        skipOnError: options?.skipOnError,
        logValidationErrors: options?.logErrors,
    });
};
export const validateQuery = (schema, options) => {
    return validate({
        query: schema,
        skipOnError: options?.skipOnError,
        logValidationErrors: options?.logErrors,
    });
};
export const validateParams = (schema, options) => {
    return validate({
        params: schema,
        skipOnError: options?.skipOnError,
        logValidationErrors: options?.logErrors,
    });
};
export const validateHeaders = (schema, options) => {
    return validate({
        headers: schema,
        skipOnError: options?.skipOnError,
        logValidationErrors: options?.logErrors,
    });
};
export const commonSchemas = {
    uuidParam: z.object({
        id: z.string().uuid('Invalid ID format'),
    }),
    pagination: z.object({
        page: z.string().optional().transform(val => val ? parseInt(val) : 1),
        limit: z.string().optional().transform(val => val ? parseInt(val) : 10),
        sortBy: z.string().optional(),
        sortOrder: z.enum(['asc', 'desc']).optional().default('desc'),
    }),
    search: z.object({
        q: z.string().min(1, 'Search query is required'),
        limit: z.string().optional().transform(val => val ? parseInt(val) : 10),
    }),
    dateRange: z.object({
        startDate: z.string().datetime().optional(),
        endDate: z.string().datetime().optional(),
    }).refine((data) => {
        if (data.startDate && data.endDate) {
            return new Date(data.startDate) <= new Date(data.endDate);
        }
        return true;
    }, {
        message: 'Start date must be before end date',
    }),
    fileUpload: z.object({
        filename: z.string().min(1),
        mimetype: z.string().regex(/^[a-zA-Z]+\/[a-zA-Z0-9\-\+\.]+$/),
        size: z.number().positive().max(10 * 1024 * 1024),
    }),
    email: z.object({
        email: z.string().email('Invalid email format'),
    }),
    password: z.object({
        password: z.string()
            .min(8, 'Password must be at least 8 characters long')
            .regex(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/, 'Password must contain at least one lowercase letter, one uppercase letter, and one number'),
    }),
    booleanQuery: z.object({
        value: z.string().optional().transform(val => {
            if (val === 'true')
                return true;
            if (val === 'false')
                return false;
            return undefined;
        }),
    }),
};
export const formatValidationError = (error) => {
    return {
        code: 'VALIDATION_ERROR',
        message: 'Request validation failed',
        details: {
            issues: error.issues.map(issue => ({
                path: issue.path.join('.'),
                message: issue.message,
                code: issue.code,
                received: issue.received,
            })),
            totalErrors: error.issues.length,
        },
    };
};
export const validateEnum = (values, message) => {
    return z.enum(values, {
        errorMap: () => ({ message: message || `Value must be one of: ${values.join(', ')}` }),
    });
};
export const validateOptionalEnum = (values, message) => {
    return validateEnum(values, message).optional();
};
export const conditionalValidation = (condition, schema) => {
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
export const transformAndValidate = (transformer, validator) => {
    return (req, res, next) => {
        try {
            const transformed = transformer(req.body);
            req.body = validator.parse(transformed);
            next();
        }
        catch (error) {
            if (error instanceof ZodError) {
                next(new ValidationError('Data transformation and validation failed', {
                    errors: error.issues,
                }));
            }
            else {
                next(error);
            }
        }
    };
};
export const sanitizeHtml = z.string().transform(val => {
    return val.replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, '');
});
export const sanitizeString = z.string().transform(val => {
    return val.trim().replace(/\s+/g, ' ');
});
export const validateRateLimit = (maxRequests, windowMs, keyGenerator) => {
    const requests = new Map();
    return (req, res, next) => {
        const key = keyGenerator ? keyGenerator(req) : req.ip || 'unknown';
        const now = Date.now();
        const windowStart = Math.floor(now / windowMs) * windowMs;
        let record = requests.get(key);
        if (!record || record.resetTime <= now) {
            record = { count: 0, resetTime: windowStart + windowMs };
            requests.set(key, record);
        }
        record.count++;
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
//# sourceMappingURL=validationMiddleware.js.map