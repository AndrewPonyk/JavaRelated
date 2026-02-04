// Custom error classes and error handling utilities

export class AppError extends Error {
  public readonly statusCode: number;
  public readonly isOperational: boolean;
  public readonly errorCode?: string;
  public readonly details?: any;

  constructor(
    message: string,
    statusCode: number = 500,
    errorCode?: string,
    details?: any,
    isOperational: boolean = true
  ) {
    super(message);

    this.name = this.constructor.name;
    this.statusCode = statusCode;
    this.isOperational = isOperational;
    this.errorCode = errorCode;
    this.details = details;

    // Capture stack trace
    Error.captureStackTrace(this, this.constructor);
  }
}

// Specific error types
export class ValidationError extends AppError {
  constructor(message: string, details?: any) {
    super(message, 400, 'VALIDATION_ERROR', details);
  }
}

export class AuthenticationError extends AppError {
  constructor(message: string = 'Authentication required') {
    super(message, 401, 'AUTHENTICATION_ERROR');
  }
}

export class AuthorizationError extends AppError {
  constructor(message: string = 'Insufficient permissions') {
    super(message, 403, 'AUTHORIZATION_ERROR');
  }
}

export class NotFoundError extends AppError {
  constructor(resource: string = 'Resource') {
    super(`${resource} not found`, 404, 'NOT_FOUND');
  }
}

export class ConflictError extends AppError {
  constructor(message: string) {
    super(message, 409, 'CONFLICT');
  }
}

export class RateLimitError extends AppError {
  constructor(message: string = 'Too many requests') {
    super(message, 429, 'RATE_LIMIT_EXCEEDED');
  }
}

export class DatabaseError extends AppError {
  constructor(message: string, details?: any) {
    super(message, 500, 'DATABASE_ERROR', details, false);
  }
}

export class CacheError extends AppError {
  constructor(message: string, details?: any) {
    super(message, 500, 'CACHE_ERROR', details, false);
  }
}

export class ExternalServiceError extends AppError {
  constructor(service: string, message?: string) {
    super(
      message || `External service error: ${service}`,
      502,
      'EXTERNAL_SERVICE_ERROR',
      { service }
    );
  }
}

// Error response interface
export interface ErrorResponse {
  success: false;
  error: {
    code: string;
    message: string;
    statusCode: number;
    details?: any;
    stack?: string;
  };
  requestId?: string;
  timestamp: string;
}

// Error handler function
export const handleError = (
  error: Error | AppError,
  requestId?: string
): ErrorResponse => {
  const timestamp = new Date().toISOString();

  // Handle known application errors
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

  // Handle Prisma errors
  if (error.name === 'PrismaClientKnownRequestError') {
    const prismaError = error as any;
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

  // Handle Zod validation errors
  if (error.name === 'ZodError') {
    const zodError = error as any;
    return {
      success: false,
      error: {
        code: 'VALIDATION_ERROR',
        message: 'Invalid input data',
        statusCode: 400,
        details: {
          issues: zodError.issues.map((issue: any) => ({
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

  // Handle JWT errors
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

  // Handle Multer errors (file upload)
  if (error.name === 'MulterError') {
    const multerError = error as any;
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

  // Handle generic errors
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

// Express error handler middleware
export const errorHandler = (error: Error | AppError, req: any, res: any, next: any) => {
  const requestId = req.requestId || 'unknown';
  const errorResponse = handleError(error, requestId);

  // Log the error
  if (error instanceof AppError && error.isOperational) {
    // Operational errors (expected) - log as warning
    console.warn('Operational Error:', {
      requestId,
      path: req.path,
      method: req.method,
      error: errorResponse.error,
    });
  } else {
    // Programming errors (unexpected) - log as error
    console.error('Programming Error:', {
      requestId,
      path: req.path,
      method: req.method,
      error: error.message,
      stack: error.stack,
    });
  }

  // Send error response
  res.status(errorResponse.error.statusCode).json(errorResponse);
};

// Async error handler wrapper
export const asyncHandler = (fn: Function) => {
  return (req: any, res: any, next: any) => {
    Promise.resolve(fn(req, res, next)).catch(next);
  };
};

// Error factory functions
export const createError = {
  validation: (message: string, details?: any) => new ValidationError(message, details),
  authentication: (message?: string) => new AuthenticationError(message),
  authorization: (message?: string) => new AuthorizationError(message),
  notFound: (resource?: string) => new NotFoundError(resource),
  conflict: (message: string) => new ConflictError(message),
  rateLimit: (message?: string) => new RateLimitError(message),
  database: (message: string, details?: any) => new DatabaseError(message, details),
  cache: (message: string, details?: any) => new CacheError(message, details),
  externalService: (service: string, message?: string) => new ExternalServiceError(service, message),
};

// Error checking utilities
export const isAppError = (error: any): error is AppError => {
  return error instanceof AppError;
};

export const isOperationalError = (error: any): boolean => {
  return isAppError(error) && error.isOperational;
};

// 404 handler
export const notFoundHandler = (req: any, res: any, next: any) => {
  const error = new NotFoundError(`Route ${req.path}`);
  next(error);
};

// Graceful shutdown helper
export const gracefulShutdown = async (server: any, signal: string) => {
  console.log(`${signal} received. Starting graceful shutdown...`);

  try {
    // Import services dynamically to avoid circular imports
    const { websocketServer } = await import('@/services/websocket/websocketServer.js');
    const { backupScheduler } = await import('@/services/backup/backupScheduler.js');

    // Stop backup scheduler first
    backupScheduler.stop();
    console.log('Backup scheduler stopped');

    // Stop WebSocket server
    await websocketServer.stop();
    console.log('WebSocket server stopped');

    // Then stop HTTP server
    server.close((err: any) => {
      if (err) {
        console.error('Error during server shutdown:', err);
        process.exit(1);
      }

      console.log('Server closed successfully');
      process.exit(0);
    });
  } catch (error) {
    console.error('Error during graceful shutdown:', error);
    process.exit(1);
  }

  // Force shutdown after 10 seconds
  setTimeout(() => {
    console.error('Forcing shutdown after 10 seconds');
    process.exit(1);
  }, 10000);
};