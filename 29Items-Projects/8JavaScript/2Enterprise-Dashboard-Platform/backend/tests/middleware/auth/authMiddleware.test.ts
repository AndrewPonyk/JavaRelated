import { Request, Response, NextFunction } from 'express';
import { authMiddleware, authService, AuthenticatedRequest } from '@/middleware/auth/authMiddleware.js';
import { mockUser, mockRedis } from '../../setup.js';
import { AuthenticationError } from '@/utils/errors.js';

// Mock dependencies
jest.mock('jsonwebtoken', () => ({
  sign: jest.fn(),
  verify: jest.fn(),
  decode: jest.fn()
}));

jest.mock('bcryptjs', () => ({
  hash: jest.fn(),
  compare: jest.fn()
}));

jest.mock('@/services/user/userService.js', () => ({
  userService: {
    getUserByEmail: jest.fn(),
    getUserById: jest.fn(),
    createUser: jest.fn(),
    updateUser: jest.fn()
  }
}));

describe('Auth Middleware', () => {
  let mockRequest: Partial<AuthenticatedRequest>;
  let mockResponse: Partial<Response>;
  let mockNext: NextFunction;

  beforeEach(() => {
    mockRequest = {
      headers: {},
      user: undefined,
      token: undefined
    };

    mockResponse = {
      status: jest.fn().mockReturnThis(),
      json: jest.fn().mockReturnThis(),
      setHeader: jest.fn()
    };

    mockNext = jest.fn();
    clearAllMocks();
  });

  describe('authMiddleware', () => {
    it('should authenticate user with valid bearer token', async () => {
      const jwt = require('jsonwebtoken');
      const { userService } = require('@/services/user/userService.js');

      mockRequest.headers = {
        authorization: 'Bearer valid-token'
      };

      jwt.verify.mockReturnValue({
        userId: mockUser.id,
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 3600
      });

      userService.getUserById.mockResolvedValue(mockUser);
      mockRedis.get.mockResolvedValue(null); // Token not blacklisted

      await authMiddleware(
        mockRequest as AuthenticatedRequest,
        mockResponse as Response,
        mockNext
      );

      expect(mockRequest.user).toEqual(mockUser);
      expect(mockRequest.token).toBe('valid-token');
      expect(mockNext).toHaveBeenCalled();
    });

    it('should reject request with no authorization header', async () => {
      mockRequest.headers = {};

      await authMiddleware(
        mockRequest as AuthenticatedRequest,
        mockResponse as Response,
        mockNext
      );

      expect(mockResponse.status).toHaveBeenCalledWith(401);
      expect(mockResponse.json).toHaveBeenCalledWith({
        success: false,
        error: {
          code: 'AUTHENTICATION_REQUIRED',
          message: 'Authentication token is required',
          statusCode: 401
        }
      });
      expect(mockNext).not.toHaveBeenCalled();
    });

    it('should reject request with malformed authorization header', async () => {
      mockRequest.headers = {
        authorization: 'InvalidFormat token'
      };

      await authMiddleware(
        mockRequest as AuthenticatedRequest,
        mockResponse as Response,
        mockNext
      );

      expect(mockResponse.status).toHaveBeenCalledWith(401);
      expect(mockResponse.json).toHaveBeenCalledWith({
        success: false,
        error: {
          code: 'INVALID_TOKEN_FORMAT',
          message: 'Invalid token format. Use Bearer <token>',
          statusCode: 401
        }
      });
    });

    it('should reject request with blacklisted token', async () => {
      const jwt = require('jsonwebtoken');

      mockRequest.headers = {
        authorization: 'Bearer blacklisted-token'
      };

      jwt.verify.mockReturnValue({
        userId: mockUser.id,
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 3600
      });

      mockRedis.get.mockResolvedValue('blacklisted'); // Token is blacklisted

      await authMiddleware(
        mockRequest as AuthenticatedRequest,
        mockResponse as Response,
        mockNext
      );

      expect(mockResponse.status).toHaveBeenCalledWith(401);
      expect(mockResponse.json).toHaveBeenCalledWith({
        success: false,
        error: {
          code: 'TOKEN_BLACKLISTED',
          message: 'Token has been revoked',
          statusCode: 401
        }
      });
    });

    it('should reject request with expired token', async () => {
      const jwt = require('jsonwebtoken');

      mockRequest.headers = {
        authorization: 'Bearer expired-token'
      };

      jwt.verify.mockImplementation(() => {
        const error = new Error('Token expired');
        (error as any).name = 'TokenExpiredError';
        throw error;
      });

      await authMiddleware(
        mockRequest as AuthenticatedRequest,
        mockResponse as Response,
        mockNext
      );

      expect(mockResponse.status).toHaveBeenCalledWith(401);
      expect(mockResponse.json).toHaveBeenCalledWith({
        success: false,
        error: {
          code: 'TOKEN_EXPIRED',
          message: 'Token has expired',
          statusCode: 401
        }
      });
    });

    it('should reject request with invalid token', async () => {
      const jwt = require('jsonwebtoken');

      mockRequest.headers = {
        authorization: 'Bearer invalid-token'
      };

      jwt.verify.mockImplementation(() => {
        const error = new Error('Invalid token');
        (error as any).name = 'JsonWebTokenError';
        throw error;
      });

      await authMiddleware(
        mockRequest as AuthenticatedRequest,
        mockResponse as Response,
        mockNext
      );

      expect(mockResponse.status).toHaveBeenCalledWith(401);
      expect(mockResponse.json).toHaveBeenCalledWith({
        success: false,
        error: {
          code: 'INVALID_TOKEN',
          message: 'Invalid token',
          statusCode: 401
        }
      });
    });

    it('should reject request when user not found', async () => {
      const jwt = require('jsonwebtoken');
      const { userService } = require('@/services/user/userService.js');

      mockRequest.headers = {
        authorization: 'Bearer valid-token'
      };

      jwt.verify.mockReturnValue({
        userId: 'non-existent-user',
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 3600
      });

      userService.getUserById.mockResolvedValue(null);
      mockRedis.get.mockResolvedValue(null);

      await authMiddleware(
        mockRequest as AuthenticatedRequest,
        mockResponse as Response,
        mockNext
      );

      expect(mockResponse.status).toHaveBeenCalledWith(401);
      expect(mockResponse.json).toHaveBeenCalledWith({
        success: false,
        error: {
          code: 'USER_NOT_FOUND',
          message: 'User not found',
          statusCode: 401
        }
      });
    });

    it('should reject request for inactive user', async () => {
      const jwt = require('jsonwebtoken');
      const { userService } = require('@/services/user/userService.js');

      mockRequest.headers = {
        authorization: 'Bearer valid-token'
      };

      jwt.verify.mockReturnValue({
        userId: mockUser.id,
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 3600
      });

      const inactiveUser = { ...mockUser, isActive: false };
      userService.getUserById.mockResolvedValue(inactiveUser);
      mockRedis.get.mockResolvedValue(null);

      await authMiddleware(
        mockRequest as AuthenticatedRequest,
        mockResponse as Response,
        mockNext
      );

      expect(mockResponse.status).toHaveBeenCalledWith(401);
      expect(mockResponse.json).toHaveBeenCalledWith({
        success: false,
        error: {
          code: 'USER_INACTIVE',
          message: 'User account is inactive',
          statusCode: 401
        }
      });
    });
  });

  describe('authService', () => {
    describe('registerUser', () => {
      const registerData = {
        email: 'test@example.com',
        password: 'SecurePassword123!',
        firstName: 'Test',
        lastName: 'User'
      };

      it('should register new user successfully', async () => {
        const bcrypt = require('bcryptjs');
        const { userService } = require('@/services/user/userService.js');

        userService.getUserByEmail.mockResolvedValue(null);
        bcrypt.hash.mockResolvedValue('hashed-password');
        userService.createUser.mockResolvedValue(mockUser);

        const result = await authService.registerUser(registerData);

        expect(result).toEqual(mockUser);
        expect(userService.getUserByEmail).toHaveBeenCalledWith(registerData.email);
        expect(bcrypt.hash).toHaveBeenCalledWith(registerData.password, 12);
        expect(userService.createUser).toHaveBeenCalledWith({
          ...registerData,
          password: 'hashed-password',
          role: 'USER'
        });
      });

      it('should throw error for existing email', async () => {
        const { userService } = require('@/services/user/userService.js');

        userService.getUserByEmail.mockResolvedValue(mockUser);

        await expect(
          authService.registerUser(registerData)
        ).rejects.toThrow('User with this email already exists');
      });

      it('should handle password hashing errors', async () => {
        const bcrypt = require('bcryptjs');
        const { userService } = require('@/services/user/userService.js');

        userService.getUserByEmail.mockResolvedValue(null);
        bcrypt.hash.mockRejectedValue(new Error('Hashing failed'));

        await expect(
          authService.registerUser(registerData)
        ).rejects.toThrow('Hashing failed');
      });
    });

    describe('authenticateUser', () => {
      const loginData = {
        email: 'test@example.com',
        password: 'SecurePassword123!'
      };

      it('should authenticate user with valid credentials', async () => {
        const bcrypt = require('bcryptjs');
        const { userService } = require('@/services/user/userService.js');

        const userWithPassword = { ...mockUser, password: 'hashed-password' };
        userService.getUserByEmail.mockResolvedValue(userWithPassword);
        bcrypt.compare.mockResolvedValue(true);
        userService.updateUser.mockResolvedValue({
          ...mockUser,
          lastLoginAt: new Date()
        });

        const result = await authService.authenticateUser(loginData.email, loginData.password);

        expect(result).toEqual(expect.objectContaining({
          id: mockUser.id,
          email: mockUser.email
        }));
        expect(bcrypt.compare).toHaveBeenCalledWith(loginData.password, 'hashed-password');
        expect(userService.updateUser).toHaveBeenCalledWith(mockUser.id, {
          lastLoginAt: expect.any(Date)
        });
      });

      it('should throw error for non-existent user', async () => {
        const { userService } = require('@/services/user/userService.js');

        userService.getUserByEmail.mockResolvedValue(null);

        await expect(
          authService.authenticateUser(loginData.email, loginData.password)
        ).rejects.toThrow('Invalid email or password');
      });

      it('should throw error for invalid password', async () => {
        const bcrypt = require('bcryptjs');
        const { userService } = require('@/services/user/userService.js');

        const userWithPassword = { ...mockUser, password: 'hashed-password' };
        userService.getUserByEmail.mockResolvedValue(userWithPassword);
        bcrypt.compare.mockResolvedValue(false);

        await expect(
          authService.authenticateUser(loginData.email, loginData.password)
        ).rejects.toThrow('Invalid email or password');
      });

      it('should throw error for inactive user', async () => {
        const bcrypt = require('bcryptjs');
        const { userService } = require('@/services/user/userService.js');

        const inactiveUser = {
          ...mockUser,
          isActive: false,
          password: 'hashed-password'
        };
        userService.getUserByEmail.mockResolvedValue(inactiveUser);
        bcrypt.compare.mockResolvedValue(true);

        await expect(
          authService.authenticateUser(loginData.email, loginData.password)
        ).rejects.toThrow('User account is inactive');
      });
    });

    describe('generateTokens', () => {
      it('should generate access and refresh tokens', () => {
        const jwt = require('jsonwebtoken');

        jwt.sign
          .mockReturnValueOnce('access-token')
          .mockReturnValueOnce('refresh-token');

        const tokens = authService.generateTokens(mockUser);

        expect(tokens).toEqual({
          accessToken: 'access-token',
          refreshToken: 'refresh-token',
          expiresIn: expect.any(Number)
        });

        expect(jwt.sign).toHaveBeenCalledTimes(2);
        expect(jwt.sign).toHaveBeenCalledWith(
          expect.objectContaining({
            userId: mockUser.id,
            email: mockUser.email,
            role: mockUser.role
          }),
          expect.any(String),
          expect.objectContaining({
            expiresIn: expect.any(String)
          })
        );
      });
    });

    describe('verifyToken', () => {
      it('should verify valid token', async () => {
        const jwt = require('jsonwebtoken');

        jwt.verify.mockReturnValue({
          userId: mockUser.id,
          email: mockUser.email,
          role: mockUser.role,
          iat: Math.floor(Date.now() / 1000),
          exp: Math.floor(Date.now() / 1000) + 3600
        });

        mockRedis.get.mockResolvedValue(null); // Not blacklisted

        const result = await authService.verifyToken('valid-token');

        expect(result).toEqual({
          userId: mockUser.id,
          email: mockUser.email,
          role: mockUser.role,
          iat: expect.any(Number),
          exp: expect.any(Number)
        });
      });

      it('should reject blacklisted token', async () => {
        const jwt = require('jsonwebtoken');

        jwt.verify.mockReturnValue({
          userId: mockUser.id,
          email: mockUser.email,
          role: mockUser.role
        });

        mockRedis.get.mockResolvedValue('blacklisted');

        await expect(
          authService.verifyToken('blacklisted-token')
        ).rejects.toThrow('Token has been revoked');
      });
    });

    describe('refreshTokens', () => {
      it('should refresh tokens with valid refresh token', async () => {
        const jwt = require('jsonwebtoken');
        const { userService } = require('@/services/user/userService.js');

        jwt.verify.mockReturnValue({
          userId: mockUser.id,
          type: 'refresh',
          iat: Math.floor(Date.now() / 1000),
          exp: Math.floor(Date.now() / 1000) + 3600
        });

        userService.getUserById.mockResolvedValue(mockUser);
        mockRedis.get.mockResolvedValue(null); // Not blacklisted

        jwt.sign
          .mockReturnValueOnce('new-access-token')
          .mockReturnValueOnce('new-refresh-token');

        const result = await authService.refreshTokens('valid-refresh-token');

        expect(result).toEqual({
          accessToken: 'new-access-token',
          refreshToken: 'new-refresh-token',
          expiresIn: expect.any(Number)
        });
      });

      it('should reject non-refresh token', async () => {
        const jwt = require('jsonwebtoken');

        jwt.verify.mockReturnValue({
          userId: mockUser.id,
          type: 'access', // Not a refresh token
          iat: Math.floor(Date.now() / 1000),
          exp: Math.floor(Date.now() / 1000) + 3600
        });

        await expect(
          authService.refreshTokens('access-token')
        ).rejects.toThrow('Invalid refresh token');
      });
    });

    describe('logout', () => {
      it('should blacklist token on logout', async () => {
        const jwt = require('jsonwebtoken');

        jwt.decode.mockReturnValue({
          exp: Math.floor(Date.now() / 1000) + 3600
        });

        mockRedis.setex.mockResolvedValue('OK');

        await authService.logout('valid-token');

        expect(mockRedis.setex).toHaveBeenCalledWith(
          'blacklist:valid-token',
          3600,
          'blacklisted'
        );
      });

      it('should handle tokens without expiration', async () => {
        const jwt = require('jsonwebtoken');

        jwt.decode.mockReturnValue({}); // No exp claim

        mockRedis.setex.mockResolvedValue('OK');

        await authService.logout('token-without-exp');

        expect(mockRedis.setex).toHaveBeenCalledWith(
          'blacklist:token-without-exp',
          86400, // Default 24 hours
          'blacklisted'
        );
      });
    });

    describe('changePassword', () => {
      it('should change password with valid current password', async () => {
        const bcrypt = require('bcryptjs');
        const { userService } = require('@/services/user/userService.js');

        const userWithPassword = { ...mockUser, password: 'old-hashed-password' };
        userService.getUserById.mockResolvedValue(userWithPassword);
        bcrypt.compare.mockResolvedValue(true);
        bcrypt.hash.mockResolvedValue('new-hashed-password');
        userService.updateUser.mockResolvedValue(mockUser);

        await authService.changePassword(mockUser.id, 'oldPassword', 'newPassword');

        expect(bcrypt.compare).toHaveBeenCalledWith('oldPassword', 'old-hashed-password');
        expect(bcrypt.hash).toHaveBeenCalledWith('newPassword', 12);
        expect(userService.updateUser).toHaveBeenCalledWith(mockUser.id, {
          password: 'new-hashed-password'
        });
      });

      it('should throw error for incorrect current password', async () => {
        const bcrypt = require('bcryptjs');
        const { userService } = require('@/services/user/userService.js');

        const userWithPassword = { ...mockUser, password: 'hashed-password' };
        userService.getUserById.mockResolvedValue(userWithPassword);
        bcrypt.compare.mockResolvedValue(false);

        await expect(
          authService.changePassword(mockUser.id, 'wrongPassword', 'newPassword')
        ).rejects.toThrow('Current password is incorrect');
      });
    });
  });
});