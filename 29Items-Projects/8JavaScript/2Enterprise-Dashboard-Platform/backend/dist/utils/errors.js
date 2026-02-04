export class AppError extends Error {
    statusCode;
    isOperational;
    errorCode;
    details;
    constructor(message, statusCode = 500, errorCode, details, isOperational = true) {
        super(message);
        this.name = this.constructor.name;
        this.statusCode = statusCode;
        this.isOperational = isOperational;
        this.errorCode = errorCode;
        this.details = details;
        Error.captureStackTrace(this, this.constructor);
    }
}
export class ValidationError extends AppError {
    constructor(message, details) {
        super(message, 400, 'VALIDATION_ERROR', details);
    }
}
export class AuthenticationError extends AppError {
    constructor(message = 'Authentication required') {
        super(message, 401, 'AUTHENTICATION_ERROR');
    }
}
export class AuthorizationError extends AppError {
    constructor(message = 'Insufficient permissions') {
        super(message, 403, 'AUTHORIZATION_ERROR');
    }
}
export class NotFoundError extends AppError {
    constructor(resource = 'Resource') {
        super(`${resource} not found`, 404, 'NOT_FOUND');
    }
}
export class ConflictError extends AppError {
    constructor(message) {
        super(message, 409, 'CONFLICT');
    }
}
export class RateLimitError extends AppError {
    constructor(message = 'Too many requests') {
        super(message, 429, 'RATE_LIMIT_EXCEEDED');
    }
}
export class DatabaseError extends AppError {
    constructor(message, details) {
        super(message, 500, 'DATABASE_ERROR', details, false);
    }
}
export class CacheError extends AppError {
    constructor(message, details) {
        super(message, 500, 'CACHE_ERROR', details, false);
    }
}
export class ExternalServiceError extends AppError {
    constructor(service, message) {
        super(message || `External service error: ${service}`, 502, 'EXTERNAL_SERVICE_ERROR', { service });
    }
}
export const handleError = (error, requestId) => {
    const timestamp = new Date().toISOString();
    if (error instanceof AppError) {
        return {
            success: false,
            error: {
                code: error.errorCode || 'APPLICATION_ERROR',
                message: error.message,
                statusCode: error.statusCode,
                details: error.details,
                ...(process.env.NODE_ENV === 'development' && { stack: error.stack }),
            },
            requestId,
            timestamp,
        };
    }
    if (error.name === 'PrismaClientKnownRequestError') {
        const prismaError = error;
        switch (prismaError.code) {
            case 'P2002':
                return {
                    success: false,
                    error: {
                        code: 'UNIQUE_CONSTRAINT_VIOLATION',
                        message: 'A record with this data already exists',
                        statusCode: 409,
                        details: { field: prismaError.meta?.target },
                    },
                    requestId,
                    timestamp,
                };
            case 'P2025':
                return {
                    success: false,
                    error: {
                        code: 'RECORD_NOT_FOUND',
                        message: 'The requested record was not found',
                        statusCode: 404,
                    },
                    requestId,
                    timestamp,
                };
            case 'P2003':
                return {
                    success: false,
                    error: {
                        code: 'FOREIGN_KEY_CONSTRAINT',
                        message: 'Foreign key constraint violation',
                        statusCode: 400,
                        details: { field: prismaError.meta?.field_name },
                    },
                    requestId,
                    timestamp,
                };
            default:
                return {
                    success: false,
                    error: {
                        code: 'DATABASE_ERROR',
                        message: 'A database error occurred',
                        statusCode: 500,
                        details: process.env.NODE_ENV === 'development' ? prismaError.meta : undefined,
                    },
                    requestId,
                    timestamp,
                };
        }
    }
    if (error.name === 'ZodError') {
        const zodError = error;
        return {
            success: false,
            error: {
                code: 'VALIDATION_ERROR',
                message: 'Invalid input data',
                statusCode: 400,
                details: {
                    issues: zodError.issues.map((issue) => ({
                        path: issue.path.join('.'),
                        message: issue.message,
                        code: issue.code,
                    })),
                },
            },
            requestId,
            timestamp,
        };
    }
    if (error.name === 'JsonWebTokenError') {
        return {
            success: false,
            error: {
                code: 'INVALID_TOKEN',
                message: 'Invalid authentication token',
                statusCode: 401,
            },
            requestId,
            timestamp,
        };
    }
    if (error.name === 'TokenExpiredError') {
        return {
            success: false,
            error: {
                code: 'TOKEN_EXPIRED',
                message: 'Authentication token has expired',
                statusCode: 401,
            },
            requestId,
            timestamp,
        };
    }
    if (error.name === 'MulterError') {
        const multerError = error;
        let message = 'File upload error';
        let code = 'FILE_UPLOAD_ERROR';
        switch (multerError.code) {
            case 'LIMIT_FILE_SIZE':
                message = 'File size too large';
                code = 'FILE_TOO_LARGE';
                break;
            case 'LIMIT_FILE_COUNT':
                message = 'Too many files';
                code = 'TOO_MANY_FILES';
                break;
            case 'LIMIT_UNEXPECTED_FILE':
                message = 'Unexpected field name';
                code = 'UNEXPECTED_FIELD';
                break;
        }
        return {
            success: false,
            error: {
                code,
                message,
                statusCode: 400,
                details: { field: multerError.field },
            },
            requestId,
            timestamp,
        };
    }
    return {
        success: false,
        error: {
            code: 'INTERNAL_SERVER_ERROR',
            message: process.env.NODE_ENV === 'production'
                ? 'An internal server error occurred'
                : error.message,
            statusCode: 500,
            ...(process.env.NODE_ENV === 'development' && { stack: error.stack }),
        },
        requestId,
        timestamp,
    };
};
export const errorHandler = (error, req, res, next) => {
    const requestId = req.requestId || 'unknown';
    const errorResponse = handleError(error, requestId);
    if (error instanceof AppError && error.isOperational) {
        console.warn('Operational Error:', {
            requestId,
            path: req.path,
            method: req.method,
            error: errorResponse.error,
        });
    }
    else {
        console.error('Programming Error:', {
            requestId,
            path: req.path,
            method: req.method,
            error: error.message,
            stack: error.stack,
        });
    }
    res.status(errorResponse.error.statusCode).json(errorResponse);
};
export const asyncHandler = (fn) => {
    return (req, res, next) => {
        Promise.resolve(fn(req, res, next)).catch(next);
    };
};
export const createError = {
    validation: (message, details) => new ValidationError(message, details),
    authentication: (message) => new AuthenticationError(message),
    authorization: (message) => new AuthorizationError(message),
    notFound: (resource) => new NotFoundError(resource),
    conflict: (message) => new ConflictError(message),
    rateLimit: (message) => new RateLimitError(message),
    database: (message, details) => new DatabaseError(message, details),
    cache: (message, details) => new CacheError(message, details),
    externalService: (service, message) => new ExternalServiceError(service, message),
};
export const isAppError = (error) => {
    return error instanceof AppError;
};
export const isOperationalError = (error) => {
    return isAppError(error) && error.isOperational;
};
export const notFoundHandler = (req, res, next) => {
    const error = new NotFoundError(`Route ${req.path}`);
    next(error);
};
export const gracefulShutdown = async (server, signal) => {
    console.log(`${signal} received. Starting graceful shutdown...`);
    try {
        const { websocketServer } = await import('@/services/websocket/websocketServer.js');
        const { backupScheduler } = await import('@/services/backup/backupScheduler.js');
        backupScheduler.stop();
        console.log('Backup scheduler stopped');
        await websocketServer.stop();
        console.log('WebSocket server stopped');
        server.close((err) => {
            if (err) {
                console.error('Error during server shutdown:', err);
                process.exit(1);
            }
            console.log('Server closed successfully');
            process.exit(0);
        });
    }
    catch (error) {
        console.error('Error during graceful shutdown:', error);
        process.exit(1);
    }
    setTimeout(() => {
        console.error('Forcing shutdown after 10 seconds');
        process.exit(1);
    }, 10000);
};
//# sourceMappingURL=errors.js.map