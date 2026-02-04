import { Request, Response, NextFunction } from 'express';
import {
  CSRFTokenService,
  csrfProtection,
  generateCSRFToken,
  validateCSRFToken
} from '@/middleware/security/csrfMiddleware.js';
import { mockRedis } from '../../setup.js';

// Mock crypto
jest.mock('crypto', () => ({
  randomBytes: jest.fn().mockReturnValue({
    toString: jest.fn().mockReturnValue('mocked-csrf-token-12345')
  }),
  timingSafeEqual: jest.fn()
}));

describe('CSRF Middleware', () => {
  let mockRequest: Partial<Request>;
  let mockResponse: Partial<Response>;
  let mockNext: NextFunction;
  let csrfService: CSRFTokenService;

  beforeEach(() => {
    mockRequest = {
      method: 'GET',
      headers: {},
      cookies: {},
      body: {},
      session: {}
    };

    mockResponse = {
      status: jest.fn().mockReturnThis(),
      json: jest.fn().mockReturnThis(),
      cookie: jest.fn().mockReturnThis(),
      clearCookie: jest.fn().mockReturnThis()
    };

    mockNext = jest.fn();
    csrfService = new CSRFTokenService();
    clearAllMocks();
  });

  describe('CSRFTokenService', () => {
    describe('generateToken', () => {
      it('should generate a valid CSRF token', () => {
        const token = csrfService.generateToken();

        expect(token).toBe('mocked-csrf-token-12345');
        expect(typeof token).toBe('string');
        expect(token.length).toBeGreaterThan(0);
      });

      it('should generate different tokens on multiple calls', () => {
        const crypto = require('crypto');
        crypto.randomBytes.mockReturnValueOnce({
          toString: jest.fn().mockReturnValue('token-1')
        }).mockReturnValueOnce({
          toString: jest.fn().mockReturnValue('token-2')
        });

        const token1 = csrfService.generateToken();
        const token2 = csrfService.generateToken();

        expect(token1).not.toBe(token2);
      });
    });

    describe('storeToken', () => {
      it('should store token with session ID', async () => {
        const sessionId = 'session-123';
        const token = 'csrf-token-456';

        mockRedis.setex.mockResolvedValue('OK');

        await csrfService.storeToken(sessionId, token);

        expect(mockRedis.setex).toHaveBeenCalledWith(
          'csrf:session-123',
          1800, // 30 minutes
          'csrf-token-456'
        );
      });

      it('should handle Redis storage errors', async () => {
        const sessionId = 'session-123';
        const token = 'csrf-token-456';
        const redisError = new Error('Redis connection failed');

        mockRedis.setex.mockRejectedValue(redisError);

        await expect(
          csrfService.storeToken(sessionId, token)
        ).rejects.toThrow('Redis connection failed');
      });
    });

    describe('validateAndConsumeToken', () => {
      const sessionId = 'session-123';
      const validToken = 'valid-csrf-token';

      it('should validate correct token', async () => {
        const crypto = require('crypto');

        mockRedis.get.mockResolvedValue(validToken);
        mockRedis.del.mockResolvedValue(1);
        crypto.timingSafeEqual.mockReturnValue(true);

        const isValid = await csrfService.validateAndConsumeToken(sessionId, validToken);

        expect(isValid).toBe(true);
        expect(mockRedis.get).toHaveBeenCalledWith('csrf:session-123');
        expect(mockRedis.del).toHaveBeenCalledWith('csrf:session-123');
        expect(crypto.timingSafeEqual).toHaveBeenCalled();
      });

      it('should reject invalid token', async () => {
        const crypto = require('crypto');

        mockRedis.get.mockResolvedValue(validToken);
        crypto.timingSafeEqual.mockReturnValue(false);

        const isValid = await csrfService.validateAndConsumeToken(sessionId, 'invalid-token');

        expect(isValid).toBe(false);
        expect(mockRedis.del).toHaveBeenCalledWith('csrf:session-123');
      });

      it('should reject when no stored token exists', async () => {
        mockRedis.get.mockResolvedValue(null);

        const isValid = await csrfService.validateAndConsumeToken(sessionId, validToken);

        expect(isValid).toBe(false);
        expect(mockRedis.del).not.toHaveBeenCalled();
      });

      it('should handle Redis errors gracefully', async () => {
        const redisError = new Error('Redis connection failed');
        mockRedis.get.mockRejectedValue(redisError);

        const isValid = await csrfService.validateAndConsumeToken(sessionId, validToken);

        expect(isValid).toBe(false);
      });

      it('should handle buffer comparison safely', async () => {
        const crypto = require('crypto');

        mockRedis.get.mockResolvedValue(validToken);
        mockRedis.del.mockResolvedValue(1);

        // Mock Buffer.from calls for safe comparison
        const mockBuffer1 = Buffer.from(validToken);
        const mockBuffer2 = Buffer.from(validToken);
        jest.spyOn(Buffer, 'from')
          .mockReturnValueOnce(mockBuffer1)
          .mockReturnValueOnce(mockBuffer2);

        crypto.timingSafeEqual.mockReturnValue(true);

        const isValid = await csrfService.validateAndConsumeToken(sessionId, validToken);

        expect(isValid).toBe(true);
        expect(crypto.timingSafeEqual).toHaveBeenCalledWith(mockBuffer1, mockBuffer2);
      });
    });

    describe('isTokenExpired', () => {
      it('should identify non-expired tokens', async () => {
        const sessionId = 'session-123';
        mockRedis.get.mockResolvedValue('valid-token');

        const isExpired = await csrfService.isTokenExpired(sessionId);

        expect(isExpired).toBe(false);
      });

      it('should identify expired tokens', async () => {
        const sessionId = 'session-123';
        mockRedis.get.mockResolvedValue(null);

        const isExpired = await csrfService.isTokenExpired(sessionId);

        expect(isExpired).toBe(true);
      });
    });

    describe('cleanupExpiredTokens', () => {
      it('should remove expired tokens', async () => {
        const expiredKeys = ['csrf:session-1', 'csrf:session-2', 'csrf:session-3'];

        mockRedis.keys.mockResolvedValue(expiredKeys);
        mockRedis.del.mockResolvedValue(expiredKeys.length);

        const cleanedCount = await csrfService.cleanupExpiredTokens();

        expect(cleanedCount).toBe(3);
        expect(mockRedis.keys).toHaveBeenCalledWith('csrf:*');
        expect(mockRedis.del).toHaveBeenCalledWith(...expiredKeys);
      });

      it('should handle no expired tokens', async () => {
        mockRedis.keys.mockResolvedValue([]);

        const cleanedCount = await csrfService.cleanupExpiredTokens();

        expect(cleanedCount).toBe(0);
        expect(mockRedis.del).not.toHaveBeenCalled();
      });

      it('should handle cleanup errors gracefully', async () => {
        const redisError = new Error('Redis connection failed');
        mockRedis.keys.mockRejectedValue(redisError);

        const cleanedCount = await csrfService.cleanupExpiredTokens();

        expect(cleanedCount).toBe(0);
      });
    });
  });

  describe('csrfProtection middleware', () => {
    beforeEach(() => {
      // Mock session ID
      mockRequest.session = { id: 'session-123' };
    });

    describe('GET requests (safe methods)', () => {
      it('should allow GET requests and generate CSRF token', async () => {
        mockRequest.method = 'GET';

        await csrfProtection(
          mockRequest as Request,
          mockResponse as Response,
          mockNext
        );

        expect(mockNext).toHaveBeenCalled();
        expect(mockResponse.cookie).toHaveBeenCalledWith(
          '_csrf',
          expect.any(String),
          expect.objectContaining({
            httpOnly: false,
            secure: false,
            sameSite: 'strict',
            maxAge: 1800000
          })
        );
      });

      it('should allow HEAD requests', async () => {
        mockRequest.method = 'HEAD';

        await csrfProtection(
          mockRequest as Request,
          mockResponse as Response,
          mockNext
        );

        expect(mockNext).toHaveBeenCalled();
      });

      it('should allow OPTIONS requests', async () => {
        mockRequest.method = 'OPTIONS';

        await csrfProtection(
          mockRequest as Request,
          mockResponse as Response,
          mockNext
        );

        expect(mockNext).toHaveBeenCalled();
      });
    });

    describe('POST requests (unsafe methods)', () => {
      beforeEach(() => {
        mockRequest.method = 'POST';
        mockRedis.get.mockResolvedValue('valid-csrf-token');
        mockRedis.del.mockResolvedValue(1);
      });

      it('should validate CSRF token from header', async () => {
        const crypto = require('crypto');

        mockRequest.headers = {
          'x-csrf-token': 'valid-csrf-token'
        };

        crypto.timingSafeEqual.mockReturnValue(true);

        await csrfProtection(
          mockRequest as Request,
          mockResponse as Response,
          mockNext
        );

        expect(mockNext).toHaveBeenCalled();
        expect(mockRedis.get).toHaveBeenCalledWith('csrf:session-123');
      });

      it('should validate CSRF token from cookie', async () => {
        const crypto = require('crypto');

        mockRequest.cookies = {
          '_csrf': 'valid-csrf-token'
        };

        crypto.timingSafeEqual.mockReturnValue(true);

        await csrfProtection(
          mockRequest as Request,
          mockResponse as Response,
          mockNext
        );

        expect(mockNext).toHaveBeenCalled();
      });

      it('should validate CSRF token from body', async () => {
        const crypto = require('crypto');

        mockRequest.body = {
          _csrf: 'valid-csrf-token'
        };

        crypto.timingSafeEqual.mockReturnValue(true);

        await csrfProtection(
          mockRequest as Request,
          mockResponse as Response,
          mockNext
        );

        expect(mockNext).toHaveBeenCalled();
      });

      it('should reject request with missing CSRF token', async () => {
        // No CSRF token provided

        await csrfProtection(
          mockRequest as Request,
          mockResponse as Response,
          mockNext
        );

        expect(mockResponse.status).toHaveBeenCalledWith(403);
        expect(mockResponse.json).toHaveBeenCalledWith({
          success: false,
          error: {
            code: 'CSRF_TOKEN_MISSING',
            message: 'CSRF token is required for this request',
            statusCode: 403
          }
        });
        expect(mockNext).not.toHaveBeenCalled();
      });

      it('should reject request with invalid CSRF token', async () => {
        const crypto = require('crypto');

        mockRequest.headers = {
          'x-csrf-token': 'invalid-csrf-token'
        };

        crypto.timingSafeEqual.mockReturnValue(false);

        await csrfProtection(
          mockRequest as Request,
          mockResponse as Response,
          mockNext
        );

        expect(mockResponse.status).toHaveBeenCalledWith(403);
        expect(mockResponse.json).toHaveBeenCalledWith({
          success: false,
          error: {
            code: 'CSRF_TOKEN_INVALID',
            message: 'Invalid CSRF token',
            statusCode: 403
          }
        });
      });

      it('should handle missing session gracefully', async () => {
        mockRequest.session = undefined;

        await csrfProtection(
          mockRequest as Request,
          mockResponse as Response,
          mockNext
        );

        expect(mockResponse.status).toHaveBeenCalledWith(403);
        expect(mockResponse.json).toHaveBeenCalledWith({
          success: false,
          error: {
            code: 'SESSION_REQUIRED',
            message: 'Session is required for CSRF protection',
            statusCode: 403
          }
        });
      });
    });

    describe('PUT requests', () => {
      it('should validate CSRF token for PUT requests', async () => {
        const crypto = require('crypto');

        mockRequest.method = 'PUT';
        mockRequest.headers = {
          'x-csrf-token': 'valid-csrf-token'
        };

        mockRedis.get.mockResolvedValue('valid-csrf-token');
        crypto.timingSafeEqual.mockReturnValue(true);

        await csrfProtection(
          mockRequest as Request,
          mockResponse as Response,
          mockNext
        );

        expect(mockNext).toHaveBeenCalled();
      });
    });

    describe('DELETE requests', () => {
      it('should validate CSRF token for DELETE requests', async () => {
        const crypto = require('crypto');

        mockRequest.method = 'DELETE';
        mockRequest.headers = {
          'x-csrf-token': 'valid-csrf-token'
        };

        mockRedis.get.mockResolvedValue('valid-csrf-token');
        crypto.timingSafeEqual.mockReturnValue(true);

        await csrfProtection(
          mockRequest as Request,
          mockResponse as Response,
          mockNext
        );

        expect(mockNext).toHaveBeenCalled();
      });
    });

    describe('PATCH requests', () => {
      it('should validate CSRF token for PATCH requests', async () => {
        const crypto = require('crypto');

        mockRequest.method = 'PATCH';
        mockRequest.headers = {
          'x-csrf-token': 'valid-csrf-token'
        };

        mockRedis.get.mockResolvedValue('valid-csrf-token');
        crypto.timingSafeEqual.mockReturnValue(true);

        await csrfProtection(
          mockRequest as Request,
          mockResponse as Response,
          mockNext
        );

        expect(mockNext).toHaveBeenCalled();
      });
    });

    describe('error handling', () => {
      it('should handle service errors gracefully', async () => {
        const serviceError = new Error('CSRF service failed');
        jest.spyOn(CSRFTokenService.prototype, 'validateAndConsumeToken')
          .mockRejectedValue(serviceError);

        mockRequest.method = 'POST';
        mockRequest.headers = {
          'x-csrf-token': 'some-token'
        };

        await csrfProtection(
          mockRequest as Request,
          mockResponse as Response,
          mockNext
        );

        expect(mockResponse.status).toHaveBeenCalledWith(500);
        expect(mockResponse.json).toHaveBeenCalledWith({
          success: false,
          error: {
            code: 'CSRF_VALIDATION_ERROR',
            message: 'CSRF validation failed',
            statusCode: 500
          }
        });
      });
    });
  });

  describe('generateCSRFToken function', () => {
    it('should generate and store CSRF token', async () => {
      mockRequest.session = { id: 'session-123' };
      mockRedis.setex.mockResolvedValue('OK');

      await generateCSRFToken(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      expect(mockResponse.cookie).toHaveBeenCalledWith(
        '_csrf',
        expect.any(String),
        expect.objectContaining({
          httpOnly: false,
          secure: false,
          sameSite: 'strict'
        })
      );
      expect(mockNext).toHaveBeenCalled();
    });

    it('should handle missing session', async () => {
      mockRequest.session = undefined;

      await generateCSRFToken(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      expect(mockResponse.status).toHaveBeenCalledWith(500);
      expect(mockResponse.json).toHaveBeenCalledWith({
        success: false,
        error: {
          code: 'SESSION_REQUIRED',
          message: 'Session is required to generate CSRF token',
          statusCode: 500
        }
      });
    });
  });

  describe('validateCSRFToken function', () => {
    beforeEach(() => {
      mockRequest.session = { id: 'session-123' };
    });

    it('should validate CSRF token successfully', async () => {
      const crypto = require('crypto');

      mockRequest.headers = {
        'x-csrf-token': 'valid-token'
      };

      mockRedis.get.mockResolvedValue('valid-token');
      mockRedis.del.mockResolvedValue(1);
      crypto.timingSafeEqual.mockReturnValue(true);

      await validateCSRFToken(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      expect(mockNext).toHaveBeenCalled();
    });

    it('should reject invalid CSRF token', async () => {
      const crypto = require('crypto');

      mockRequest.headers = {
        'x-csrf-token': 'invalid-token'
      };

      mockRedis.get.mockResolvedValue('valid-token');
      crypto.timingSafeEqual.mockReturnValue(false);

      await validateCSRFToken(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      expect(mockResponse.status).toHaveBeenCalledWith(403);
      expect(mockResponse.json).toHaveBeenCalledWith({
        success: false,
        error: {
          code: 'CSRF_TOKEN_INVALID',
          message: 'Invalid CSRF token',
          statusCode: 403
        }
      });
    });

    it('should handle missing token', async () => {
      // No CSRF token in headers, cookies, or body

      await validateCSRFToken(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      expect(mockResponse.status).toHaveBeenCalledWith(403);
      expect(mockResponse.json).toHaveBeenCalledWith({
        success: false,
        error: {
          code: 'CSRF_TOKEN_MISSING',
          message: 'CSRF token is required',
          statusCode: 403
        }
      });
    });
  });

  describe('integration scenarios', () => {
    it('should handle full request cycle with CSRF protection', async () => {
      const crypto = require('crypto');

      // First request (GET) - should generate token
      mockRequest.method = 'GET';
      mockRequest.session = { id: 'session-123' };

      await csrfProtection(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      expect(mockNext).toHaveBeenCalledTimes(1);
      expect(mockResponse.cookie).toHaveBeenCalled();

      // Reset mocks for second request
      mockNext.mockClear();

      // Second request (POST) - should validate token
      mockRequest.method = 'POST';
      mockRequest.headers = {
        'x-csrf-token': 'mocked-csrf-token-12345'
      };

      mockRedis.get.mockResolvedValue('mocked-csrf-token-12345');
      crypto.timingSafeEqual.mockReturnValue(true);

      await csrfProtection(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      expect(mockNext).toHaveBeenCalledTimes(1);
    });

    it('should handle token expiration between requests', async () => {
      // Simulate expired token scenario
      mockRequest.method = 'POST';
      mockRequest.session = { id: 'session-123' };
      mockRequest.headers = {
        'x-csrf-token': 'expired-token'
      };

      mockRedis.get.mockResolvedValue(null); // Token expired/not found

      await csrfProtection(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      expect(mockResponse.status).toHaveBeenCalledWith(403);
      expect(mockResponse.json).toHaveBeenCalledWith(
        expect.objectContaining({
          error: expect.objectContaining({
            code: 'CSRF_TOKEN_INVALID'
          })
        })
      );
    });
  });
});