import { Request, Response, NextFunction } from 'express';
import {
  AppError,
  ValidationError,
  AuthenticationError,
  AuthorizationError,
  NotFoundError,
  ConflictError,
  RateLimitError,
  errorHandler,
  notFoundHandler,
  createAppError,
  isOperationalError
} from '@/utils/errors.js';

describe('Error Classes', () => {
  describe('AppError', () => {
    it('should create AppError with default values', () => {
      const error = new AppError('Test error');

      expect(error).toBeInstanceOf(Error);
      expect(error).toBeInstanceOf(AppError);
      expect(error.message).toBe('Test error');
      expect(error.statusCode).toBe(500);
      expect(error.code).toBe('INTERNAL_SERVER_ERROR');
      expect(error.isOperational).toBe(true);
      expect(error.details).toBeUndefined();
    });

    it('should create AppError with custom values', () => {
      const error = new AppError(
        'Custom error',
        400,
        'CUSTOM_ERROR',
        { field: 'email', value: 'invalid' }
      );

      expect(error.message).toBe('Custom error');
      expect(error.statusCode).toBe(400);
      expect(error.code).toBe('CUSTOM_ERROR');
      expect(error.details).toEqual({ field: 'email', value: 'invalid' });
    });

    it('should capture stack trace', () => {
      const error = new AppError('Test error');

      expect(error.stack).toBeDefined();
      expect(error.stack).toContain('AppError');
    });
  });

  describe('ValidationError', () => {
    it('should create ValidationError with default code', () => {
      const error = new ValidationError('Invalid input');

      expect(error).toBeInstanceOf(AppError);
      expect(error.message).toBe('Invalid input');
      expect(error.statusCode).toBe(400);
      expect(error.code).toBe('VALIDATION_ERROR');
    });

    it('should create ValidationError with validation details', () => {
      const details = [
        { field: 'email', message: 'Email is required' },
        { field: 'password', message: 'Password too short' }
      ];
      const error = new ValidationError('Validation failed', details);

      expect(error.details).toEqual(details);
    });
  });

  describe('AuthenticationError', () => {
    it('should create AuthenticationError with default code', () => {
      const error = new AuthenticationError('Login failed');

      expect(error).toBeInstanceOf(AppError);
      expect(error.message).toBe('Login failed');
      expect(error.statusCode).toBe(401);
      expect(error.code).toBe('AUTHENTICATION_ERROR');
    });

    it('should create AuthenticationError with custom code', () => {
      const error = new AuthenticationError('Token expired', 'TOKEN_EXPIRED');

      expect(error.code).toBe('TOKEN_EXPIRED');
    });
  });

  describe('AuthorizationError', () => {
    it('should create AuthorizationError with default code', () => {
      const error = new AuthorizationError('Access denied');

      expect(error).toBeInstanceOf(AppError);
      expect(error.message).toBe('Access denied');
      expect(error.statusCode).toBe(403);
      expect(error.code).toBe('AUTHORIZATION_ERROR');
    });

    it('should include resource details', () => {
      const error = new AuthorizationError(
        'Insufficient permissions',
        'INSUFFICIENT_PERMISSIONS',
        { resource: 'dashboard', action: 'delete' }
      );

      expect(error.details).toEqual({ resource: 'dashboard', action: 'delete' });
    });
  });

  describe('NotFoundError', () => {
    it('should create NotFoundError with default code', () => {
      const error = new NotFoundError('Resource not found');

      expect(error).toBeInstanceOf(AppError);
      expect(error.message).toBe('Resource not found');
      expect(error.statusCode).toBe(404);
      expect(error.code).toBe('NOT_FOUND');
    });

    it('should include resource information', () => {
      const error = new NotFoundError(
        'Dashboard not found',
        'DASHBOARD_NOT_FOUND',
        { id: 'dash_123' }
      );

      expect(error.code).toBe('DASHBOARD_NOT_FOUND');
      expect(error.details).toEqual({ id: 'dash_123' });
    });
  });

  describe('ConflictError', () => {
    it('should create ConflictError with default code', () => {
      const error = new ConflictError('Resource already exists');

      expect(error).toBeInstanceOf(AppError);
      expect(error.message).toBe('Resource already exists');
      expect(error.statusCode).toBe(409);
      expect(error.code).toBe('CONFLICT_ERROR');
    });

    it('should include conflict details', () => {
      const error = new ConflictError(
        'Email already exists',
        'EMAIL_CONFLICT',
        { email: 'test@example.com' }
      );

      expect(error.code).toBe('EMAIL_CONFLICT');
      expect(error.details).toEqual({ email: 'test@example.com' });
    });
  });

  describe('RateLimitError', () => {
    it('should create RateLimitError with default code', () => {
      const error = new RateLimitError('Rate limit exceeded');

      expect(error).toBeInstanceOf(AppError);
      expect(error.message).toBe('Rate limit exceeded');
      expect(error.statusCode).toBe(429);
      expect(error.code).toBe('RATE_LIMIT_EXCEEDED');
    });

    it('should include rate limit details', () => {
      const details = {
        limit: 100,
        remaining: 0,
        resetTime: new Date(),
        retryAfter: 60
      };
      const error = new RateLimitError('Rate limit exceeded', details);

      expect(error.details).toEqual(details);
    });
  });
});

describe('Error Handler Middleware', () => {
  let mockRequest: Partial<Request>;
  let mockResponse: Partial<Response>;
  let mockNext: NextFunction;

  beforeEach(() => {
    mockRequest = {
      method: 'GET',
      url: '/test',
      ip: '127.0.0.1',
      headers: { 'user-agent': 'test-agent' }
    };

    mockResponse = {
      status: jest.fn().mockReturnThis(),
      json: jest.fn().mockReturnThis(),
      headersSent: false
    };

    mockNext = jest.fn();

    // Mock logger
    const mockLogger = {
      error: jest.fn(),
      warn: jest.fn(),
      info: jest.fn()
    };
    jest.doMock('@/utils/logger.js', () => ({
      logger: mockLogger
    }));
  });

  describe('errorHandler', () => {
    it('should handle AppError correctly', () => {
      const error = new ValidationError('Invalid input', [
        { field: 'email', message: 'Email is required' }
      ]);

      errorHandler(
        error,
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      expect(mockResponse.status).toHaveBeenCalledWith(400);
      expect(mockResponse.json).toHaveBeenCalledWith({
        success: false,
        error: {
          code: 'VALIDATION_ERROR',
          message: 'Invalid input',
          statusCode: 400,
          details: [
            { field: 'email', message: 'Email is required' }
          ]
        },
        timestamp: expect.any(String),
        path: '/test'
      });
    });

    it('should handle generic Error as 500', () => {
      const error = new Error('Unexpected error');

      errorHandler(
        error,
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      expect(mockResponse.status).toHaveBeenCalledWith(500);
      expect(mockResponse.json).toHaveBeenCalledWith({
        success: false,
        error: {
          code: 'INTERNAL_SERVER_ERROR',
          message: 'Internal server error',
          statusCode: 500
        },
        timestamp: expect.any(String),
        path: '/test'
      });
    });

    it('should include error details in development', () => {
      // Mock development environment
      jest.doMock('@/config/environment.js', () => ({
        isDevelopment: () => true
      }));

      const error = new Error('Debug error');
      error.stack = 'Error: Debug error\n    at test.js:1:1';

      errorHandler(
        error,
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      expect(mockResponse.json).toHaveBeenCalledWith(
        expect.objectContaining({
          error: expect.objectContaining({
            stack: 'Error: Debug error\n    at test.js:1:1',
            details: 'Debug error'
          })
        })
      );
    });

    it('should not send response if headers already sent', () => {
      mockResponse.headersSent = true;
      const error = new AppError('Test error');

      errorHandler(
        error,
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      expect(mockResponse.status).not.toHaveBeenCalled();
      expect(mockResponse.json).not.toHaveBeenCalled();
      expect(mockNext).toHaveBeenCalledWith(error);
    });

    it('should handle Prisma validation errors', () => {
      const prismaError = {
        name: 'PrismaClientValidationError',
        message: 'Invalid field value',
        code: 'P2002',
        meta: { target: ['email'] }
      };

      errorHandler(
        prismaError as any,
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      expect(mockResponse.status).toHaveBeenCalledWith(400);
      expect(mockResponse.json).toHaveBeenCalledWith(
        expect.objectContaining({
          error: expect.objectContaining({
            code: 'VALIDATION_ERROR',
            message: 'Invalid field value'
          })
        })
      );
    });

    it('should handle Prisma unique constraint errors', () => {
      const prismaError = {
        name: 'PrismaClientKnownRequestError',
        message: 'Unique constraint failed',
        code: 'P2002',
        meta: { target: ['email'] }
      };

      errorHandler(
        prismaError as any,
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      expect(mockResponse.status).toHaveBeenCalledWith(409);
      expect(mockResponse.json).toHaveBeenCalledWith(
        expect.objectContaining({
          error: expect.objectContaining({
            code: 'UNIQUE_CONSTRAINT_VIOLATION',
            message: 'Resource already exists'
          })
        })
      );
    });

    it('should handle JWT errors', () => {
      const jwtError = {
        name: 'JsonWebTokenError',
        message: 'Invalid token'
      };

      errorHandler(
        jwtError as any,
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      expect(mockResponse.status).toHaveBeenCalledWith(401);
      expect(mockResponse.json).toHaveBeenCalledWith(
        expect.objectContaining({
          error: expect.objectContaining({
            code: 'INVALID_TOKEN',
            message: 'Invalid token'
          })
        })
      );
    });

    it('should handle validation errors with multiple issues', () => {
      const validationError = new ValidationError('Multiple validation errors', [
        { field: 'email', message: 'Email is required' },
        { field: 'password', message: 'Password is too short' },
        { field: 'name', message: 'Name is required' }
      ]);

      errorHandler(
        validationError,
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      expect(mockResponse.json).toHaveBeenCalledWith(
        expect.objectContaining({
          error: expect.objectContaining({
            details: [
              { field: 'email', message: 'Email is required' },
              { field: 'password', message: 'Password is too short' },
              { field: 'name', message: 'Name is required' }
            ]
          })
        })
      );
    });

    it('should include request context in error response', () => {
      mockRequest.method = 'POST';
      mockRequest.url = '/api/users';
      mockRequest.ip = '192.168.1.100';
      mockRequest.headers = {
        'user-agent': 'Mozilla/5.0',
        'x-request-id': 'req-123'
      };

      const error = new NotFoundError('User not found');

      errorHandler(
        error,
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      expect(mockResponse.json).toHaveBeenCalledWith(
        expect.objectContaining({
          path: '/api/users',
          method: 'POST',
          requestId: 'req-123'
        })
      );
    });
  });

  describe('notFoundHandler', () => {
    it('should handle 404 errors', () => {
      mockRequest.originalUrl = '/api/nonexistent';

      notFoundHandler(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      expect(mockResponse.status).toHaveBeenCalledWith(404);
      expect(mockResponse.json).toHaveBeenCalledWith({
        success: false,
        error: {
          code: 'NOT_FOUND',
          message: 'The requested resource was not found',
          statusCode: 404,
          path: '/api/nonexistent'
        },
        timestamp: expect.any(String)
      });
    });

    it('should handle missing originalUrl', () => {
      mockRequest.originalUrl = undefined;
      mockRequest.url = '/test';

      notFoundHandler(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      expect(mockResponse.json).toHaveBeenCalledWith(
        expect.objectContaining({
          error: expect.objectContaining({
            path: '/test'
          })
        })
      );
    });
  });
});

describe('Error Utilities', () => {
  describe('createAppError', () => {
    it('should create AppError with provided parameters', () => {
      const error = createAppError(
        'Custom error',
        400,
        'CUSTOM_CODE',
        { field: 'test' }
      );

      expect(error).toBeInstanceOf(AppError);
      expect(error.message).toBe('Custom error');
      expect(error.statusCode).toBe(400);
      expect(error.code).toBe('CUSTOM_CODE');
      expect(error.details).toEqual({ field: 'test' });
    });

    it('should use defaults when parameters are not provided', () => {
      const error = createAppError('Simple error');

      expect(error.statusCode).toBe(500);
      expect(error.code).toBe('INTERNAL_SERVER_ERROR');
      expect(error.details).toBeUndefined();
    });
  });

  describe('isOperationalError', () => {
    it('should identify operational errors', () => {
      const operationalError = new AppError('Operational error');
      const result = isOperationalError(operationalError);

      expect(result).toBe(true);
    });

    it('should identify non-operational errors', () => {
      const programmingError = new Error('Programming error');
      const result = isOperationalError(programmingError);

      expect(result).toBe(false);
    });

    it('should handle null/undefined errors', () => {
      expect(isOperationalError(null)).toBe(false);
      expect(isOperationalError(undefined)).toBe(false);
    });

    it('should handle objects without isOperational property', () => {
      const genericObject = { message: 'Not an error' };
      const result = isOperationalError(genericObject as any);

      expect(result).toBe(false);
    });

    it('should handle AppError subclasses', () => {
      const validationError = new ValidationError('Validation failed');
      const authError = new AuthenticationError('Auth failed');
      const notFoundError = new NotFoundError('Not found');

      expect(isOperationalError(validationError)).toBe(true);
      expect(isOperationalError(authError)).toBe(true);
      expect(isOperationalError(notFoundError)).toBe(true);
    });
  });
});

describe('Error Integration', () => {
  it('should handle async error propagation', async () => {
    const asyncError = async () => {
      throw new ValidationError('Async validation failed');
    };

    await expect(asyncError()).rejects.toThrow(ValidationError);
    await expect(asyncError()).rejects.toThrow('Async validation failed');
  });

  it('should maintain error chain information', () => {
    const originalError = new Error('Original error');
    const wrappedError = new AppError('Wrapped error', 500, 'WRAPPED', {
      originalError: originalError.message,
      originalStack: originalError.stack
    });

    expect(wrappedError.details).toEqual({
      originalError: 'Original error',
      originalStack: originalError.stack
    });
  });

  it('should handle error serialization', () => {
    const error = new ValidationError('Serialization test', [
      { field: 'email', message: 'Invalid email' }
    ]);

    const serialized = JSON.stringify({
      message: error.message,
      code: error.code,
      statusCode: error.statusCode,
      details: error.details
    });

    const parsed = JSON.parse(serialized);

    expect(parsed).toEqual({
      message: 'Serialization test',
      code: 'VALIDATION_ERROR',
      statusCode: 400,
      details: [
        { field: 'email', message: 'Invalid email' }
      ]
    });
  });

  it('should provide consistent error format across different error types', () => {
    const errors = [
      new ValidationError('Validation failed'),
      new AuthenticationError('Auth failed'),
      new AuthorizationError('Access denied'),
      new NotFoundError('Not found'),
      new ConflictError('Conflict'),
      new RateLimitError('Rate limited')
    ];

    errors.forEach(error => {
      expect(error).toHaveProperty('message');
      expect(error).toHaveProperty('statusCode');
      expect(error).toHaveProperty('code');
      expect(error).toHaveProperty('isOperational', true);
      expect(error).toBeInstanceOf(AppError);
      expect(error).toBeInstanceOf(Error);
    });
  });
});