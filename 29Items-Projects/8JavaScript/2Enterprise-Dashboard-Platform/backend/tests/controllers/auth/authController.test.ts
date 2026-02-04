import { Request, Response } from 'express';
import { AuthController } from '@/controllers/auth/authController.js';
import { authService } from '@/middleware/auth/authMiddleware.js';
import { userService } from '@/services/user/userService.js';
import { mockUser } from '../../setup.js';
import { AppError, AuthenticationError } from '@/utils/errors.js';

// Mock dependencies
jest.mock('@/middleware/auth/authMiddleware.js', () => ({
  authService: {
    registerUser: jest.fn(),
    authenticateUser: jest.fn(),
    generateTokens: jest.fn(),
    refreshTokens: jest.fn(),
    logout: jest.fn(),
    changePassword: jest.fn(),
    initiatePasswordReset: jest.fn(),
    resetPassword: jest.fn(),
  },
  authSchemas: {
    register: {
      parse: jest.fn()
    },
    login: {
      parse: jest.fn()
    },
    refreshToken: {
      parse: jest.fn()
    },
    changePassword: {
      parse: jest.fn()
    },
    resetPassword: {
      parse: jest.fn()
    },
    confirmResetPassword: {
      parse: jest.fn()
    }
  },
  AuthenticatedRequest: {}
}));

jest.mock('@/services/user/userService.js', () => ({
  userService: {
    getUserById: jest.fn()
  }
}));

jest.mock('@/utils/logger.js', () => ({
  logger: {
    info: jest.fn(),
    warn: jest.fn(),
    error: jest.fn()
  },
  logAuthEvent: jest.fn(),
  logSecurityEvent: jest.fn()
}));

describe('AuthController', () => {
  let authController: AuthController;
  let mockRequest: Partial<Request>;
  let mockResponse: Partial<Response>;
  let mockNext: jest.Mock;

  beforeEach(() => {
    authController = new AuthController();
    clearAllMocks();

    mockRequest = {
      body: {},
      ip: '127.0.0.1',
      connection: { remoteAddress: '127.0.0.1' },
      get: jest.fn().mockReturnValue('test-user-agent'),
      user: undefined,
      token: undefined
    };

    mockResponse = {
      status: jest.fn().mockReturnThis(),
      json: jest.fn().mockReturnThis(),
    };

    mockNext = jest.fn();

    // Reset rate limiting state
    (authController as any).constructor();
  });

  describe('register', () => {
    const validRegisterData = {
      email: 'test@example.com',
      password: 'SecurePassword123!',
      firstName: 'Test',
      lastName: 'User'
    };

    beforeEach(() => {
      const { authSchemas } = require('@/middleware/auth/authMiddleware.js');
      authSchemas.register.parse.mockReturnValue(validRegisterData);
    });

    it('should register user successfully', async () => {
      const newUser = { ...mockUser, ...validRegisterData };
      const mockTokens = { accessToken: 'access-token', refreshToken: 'refresh-token' };

      authService.registerUser = jest.fn().mockResolvedValue(newUser);
      authService.generateTokens = jest.fn().mockReturnValue(mockTokens);

      await authController.register(mockRequest as Request, mockResponse as Response);

      expect(authService.registerUser).toHaveBeenCalledWith({
        email: validRegisterData.email,
        password: validRegisterData.password,
        firstName: validRegisterData.firstName,
        lastName: validRegisterData.lastName
      });
      expect(authService.generateTokens).toHaveBeenCalledWith(newUser);
      expect(mockResponse.status).toHaveBeenCalledWith(201);
      expect(mockResponse.json).toHaveBeenCalledWith({
        success: true,
        data: {
          user: expect.objectContaining({
            id: newUser.id,
            email: newUser.email,
            firstName: newUser.firstName,
            lastName: newUser.lastName
          }),
          tokens: mockTokens
        },
        message: 'User registered successfully'
      });
    });

    it('should handle validation errors', async () => {
      const validationError = new Error('Email is required');
      const { authSchemas } = require('@/middleware/auth/authMiddleware.js');
      authSchemas.register.parse.mockImplementation(() => {
        throw validationError;
      });

      await expect(
        authController.register(mockRequest as Request, mockResponse as Response)
      ).rejects.toThrow('Email is required');
    });

    it('should handle duplicate email registration', async () => {
      const duplicateError = new Error('Email already exists');
      authService.registerUser = jest.fn().mockRejectedValue(duplicateError);

      await expect(
        authController.register(mockRequest as Request, mockResponse as Response)
      ).rejects.toThrow('Email already exists');
    });

    it('should exclude password from user response', async () => {
      const newUserWithPassword = { ...mockUser, password: 'hashed-password' };
      const mockTokens = { accessToken: 'access-token', refreshToken: 'refresh-token' };

      authService.registerUser = jest.fn().mockResolvedValue(newUserWithPassword);
      authService.generateTokens = jest.fn().mockReturnValue(mockTokens);

      await authController.register(mockRequest as Request, mockResponse as Response);

      const responseCall = (mockResponse.json as jest.Mock).mock.calls[0][0];
      expect(responseCall.data.user).not.toHaveProperty('password');
    });
  });

  describe('login', () => {
    const validLoginData = {
      email: 'test@example.com',
      password: 'SecurePassword123!'
    };

    beforeEach(() => {
      const { authSchemas } = require('@/middleware/auth/authMiddleware.js');
      authSchemas.login.parse.mockReturnValue(validLoginData);
    });

    it('should login user successfully', async () => {
      const user = { ...mockUser, lastLogin: new Date() };
      const mockTokens = { accessToken: 'access-token', refreshToken: 'refresh-token' };

      authService.authenticateUser = jest.fn().mockResolvedValue(user);
      authService.generateTokens = jest.fn().mockReturnValue(mockTokens);

      await authController.login(mockRequest as Request, mockResponse as Response);

      expect(authService.authenticateUser).toHaveBeenCalledWith(
        validLoginData.email,
        validLoginData.password
      );
      expect(authService.generateTokens).toHaveBeenCalledWith(user);
      expect(mockResponse.json).toHaveBeenCalledWith({
        success: true,
        data: {
          user: expect.objectContaining({
            id: user.id,
            email: user.email,
            lastLogin: user.lastLogin
          }),
          tokens: mockTokens
        },
        message: 'Login successful'
      });
    });

    it('should handle invalid credentials', async () => {
      const authError = new AuthenticationError('Invalid credentials');
      authService.authenticateUser = jest.fn().mockRejectedValue(authError);

      await expect(
        authController.login(mockRequest as Request, mockResponse as Response)
      ).rejects.toThrow('Invalid credentials');
    });

    it('should handle rate limiting after multiple failed attempts', async () => {
      const authError = new AuthenticationError('Invalid credentials');
      authService.authenticateUser = jest.fn().mockRejectedValue(authError);

      // Simulate 5 failed login attempts
      for (let i = 0; i < 5; i++) {
        try {
          await authController.login(mockRequest as Request, mockResponse as Response);
        } catch (error) {
          // Expected to fail
        }
      }

      // 6th attempt should be rate limited
      await expect(
        authController.login(mockRequest as Request, mockResponse as Response)
      ).rejects.toThrow(/Too many login attempts/);
    });

    it('should clear failed attempts on successful login', async () => {
      const authError = new AuthenticationError('Invalid credentials');
      const user = { ...mockUser, lastLogin: new Date() };
      const mockTokens = { accessToken: 'access-token', refreshToken: 'refresh-token' };

      // First attempt fails
      authService.authenticateUser = jest.fn().mockRejectedValueOnce(authError);

      try {
        await authController.login(mockRequest as Request, mockResponse as Response);
      } catch (error) {
        // Expected to fail
      }

      // Second attempt succeeds
      authService.authenticateUser = jest.fn().mockResolvedValue(user);
      authService.generateTokens = jest.fn().mockReturnValue(mockTokens);

      await expect(
        authController.login(mockRequest as Request, mockResponse as Response)
      ).resolves.not.toThrow();

      expect(mockResponse.json).toHaveBeenCalledWith(
        expect.objectContaining({
          success: true,
          message: 'Login successful'
        })
      );
    });

    it('should handle missing IP address gracefully', async () => {
      mockRequest.ip = undefined;
      mockRequest.connection = {};

      const user = mockUser;
      const mockTokens = { accessToken: 'access-token', refreshToken: 'refresh-token' };

      authService.authenticateUser = jest.fn().mockResolvedValue(user);
      authService.generateTokens = jest.fn().mockReturnValue(mockTokens);

      await expect(
        authController.login(mockRequest as Request, mockResponse as Response)
      ).resolves.not.toThrow();
    });
  });

  describe('refresh', () => {
    const validRefreshData = {
      refreshToken: 'valid-refresh-token'
    };

    beforeEach(() => {
      const { authSchemas } = require('@/middleware/auth/authMiddleware.js');
      authSchemas.refreshToken.parse.mockReturnValue(validRefreshData);
    });

    it('should refresh tokens successfully', async () => {
      const newTokens = { accessToken: 'new-access-token', refreshToken: 'new-refresh-token' };
      authService.refreshTokens = jest.fn().mockResolvedValue(newTokens);

      await authController.refresh(mockRequest as Request, mockResponse as Response);

      expect(authService.refreshTokens).toHaveBeenCalledWith(validRefreshData.refreshToken);
      expect(mockResponse.json).toHaveBeenCalledWith({
        success: true,
        data: { tokens: newTokens },
        message: 'Tokens refreshed successfully'
      });
    });

    it('should handle invalid refresh token', async () => {
      const tokenError = new AuthenticationError('Invalid refresh token');
      authService.refreshTokens = jest.fn().mockRejectedValue(tokenError);

      await expect(
        authController.refresh(mockRequest as Request, mockResponse as Response)
      ).rejects.toThrow('Invalid refresh token');
    });
  });

  describe('logout', () => {
    it('should logout user successfully', async () => {
      mockRequest.user = mockUser;
      mockRequest.token = 'valid-access-token';

      authService.logout = jest.fn().mockResolvedValue(true);

      await authController.logout(mockRequest as any, mockResponse as Response);

      expect(authService.logout).toHaveBeenCalledWith('valid-access-token');
      expect(mockResponse.json).toHaveBeenCalledWith({
        success: true,
        message: 'Logout successful'
      });
    });

    it('should handle unauthenticated logout', async () => {
      mockRequest.user = undefined;
      mockRequest.token = undefined;

      await expect(
        authController.logout(mockRequest as any, mockResponse as Response)
      ).rejects.toThrow('User not authenticated');
    });

    it('should handle logout service errors', async () => {
      mockRequest.user = mockUser;
      mockRequest.token = 'valid-access-token';

      const logoutError = new Error('Logout service failed');
      authService.logout = jest.fn().mockRejectedValue(logoutError);

      await expect(
        authController.logout(mockRequest as any, mockResponse as Response)
      ).rejects.toThrow('Logout service failed');
    });
  });

  describe('changePassword', () => {
    const validPasswordData = {
      currentPassword: 'OldPassword123!',
      newPassword: 'NewPassword123!'
    };

    beforeEach(() => {
      mockRequest.user = mockUser;
      const { authSchemas } = require('@/middleware/auth/authMiddleware.js');
      authSchemas.changePassword.parse.mockReturnValue(validPasswordData);
    });

    it('should change password successfully', async () => {
      authService.changePassword = jest.fn().mockResolvedValue(true);

      await authController.changePassword(mockRequest as any, mockResponse as Response);

      expect(authService.changePassword).toHaveBeenCalledWith(
        mockUser.id,
        validPasswordData.currentPassword,
        validPasswordData.newPassword
      );
      expect(mockResponse.json).toHaveBeenCalledWith({
        success: true,
        message: 'Password changed successfully'
      });
    });

    it('should handle unauthenticated password change', async () => {
      mockRequest.user = undefined;

      await expect(
        authController.changePassword(mockRequest as any, mockResponse as Response)
      ).rejects.toThrow('User not authenticated');
    });

    it('should handle invalid current password', async () => {
      const passwordError = new AuthenticationError('Current password is incorrect');
      authService.changePassword = jest.fn().mockRejectedValue(passwordError);

      await expect(
        authController.changePassword(mockRequest as any, mockResponse as Response)
      ).rejects.toThrow('Current password is incorrect');
    });
  });

  describe('forgotPassword', () => {
    const validResetData = {
      email: 'test@example.com'
    };

    beforeEach(() => {
      const { authSchemas } = require('@/middleware/auth/authMiddleware.js');
      authSchemas.resetPassword.parse.mockReturnValue(validResetData);
    });

    it('should initiate password reset successfully', async () => {
      authService.initiatePasswordReset = jest.fn().mockResolvedValue(true);

      await authController.forgotPassword(mockRequest as Request, mockResponse as Response);

      expect(authService.initiatePasswordReset).toHaveBeenCalledWith(validResetData.email);
      expect(mockResponse.json).toHaveBeenCalledWith({
        success: true,
        message: 'If an account with that email exists, a password reset link has been sent'
      });
    });

    it('should prevent email enumeration on service errors', async () => {
      const serviceError = new Error('User not found');
      authService.initiatePasswordReset = jest.fn().mockRejectedValue(serviceError);

      await authController.forgotPassword(mockRequest as Request, mockResponse as Response);

      // Should still return success to prevent email enumeration
      expect(mockResponse.json).toHaveBeenCalledWith({
        success: true,
        message: 'If an account with that email exists, a password reset link has been sent'
      });
    });
  });

  describe('resetPassword', () => {
    const validResetData = {
      token: 'valid-reset-token',
      newPassword: 'NewPassword123!'
    };

    beforeEach(() => {
      const { authSchemas } = require('@/middleware/auth/authMiddleware.js');
      authSchemas.confirmResetPassword.parse.mockReturnValue(validResetData);
    });

    it('should reset password successfully', async () => {
      authService.resetPassword = jest.fn().mockResolvedValue(true);

      await authController.resetPassword(mockRequest as Request, mockResponse as Response);

      expect(authService.resetPassword).toHaveBeenCalledWith(
        validResetData.token,
        validResetData.newPassword
      );
      expect(mockResponse.json).toHaveBeenCalledWith({
        success: true,
        message: 'Password reset successfully'
      });
    });

    it('should handle invalid reset token', async () => {
      const tokenError = new AuthenticationError('Invalid or expired reset token');
      authService.resetPassword = jest.fn().mockRejectedValue(tokenError);

      await expect(
        authController.resetPassword(mockRequest as Request, mockResponse as Response)
      ).rejects.toThrow('Invalid or expired reset token');
    });
  });

  describe('verifyToken', () => {
    it('should verify valid token successfully', async () => {
      mockRequest.user = mockUser;

      await authController.verifyToken(mockRequest as any, mockResponse as Response);

      expect(mockResponse.json).toHaveBeenCalledWith({
        success: true,
        data: {
          user: expect.objectContaining({
            id: mockUser.id,
            email: mockUser.email,
            firstName: mockUser.firstName,
            lastName: mockUser.lastName
          })
        },
        message: 'Token is valid'
      });
    });

    it('should handle invalid token', async () => {
      mockRequest.user = undefined;

      await expect(
        authController.verifyToken(mockRequest as any, mockResponse as Response)
      ).rejects.toThrow('Invalid token');
    });

    it('should exclude sensitive data from user response', async () => {
      const userWithSensitiveData = { ...mockUser, password: 'hashed-password' };
      mockRequest.user = userWithSensitiveData;

      await authController.verifyToken(mockRequest as any, mockResponse as Response);

      const responseCall = (mockResponse.json as jest.Mock).mock.calls[0][0];
      expect(responseCall.data.user).not.toHaveProperty('password');
    });
  });

  describe('getCurrentUser', () => {
    it('should return current user profile successfully', async () => {
      mockRequest.user = mockUser;
      userService.getUserById = jest.fn().mockResolvedValue(mockUser);

      await authController.getCurrentUser(mockRequest as any, mockResponse as Response);

      expect(userService.getUserById).toHaveBeenCalledWith(mockUser.id, true);
      expect(mockResponse.json).toHaveBeenCalledWith({
        success: true,
        data: { user: mockUser },
        message: 'User profile retrieved successfully'
      });
    });

    it('should handle unauthenticated request', async () => {
      mockRequest.user = undefined;

      await expect(
        authController.getCurrentUser(mockRequest as any, mockResponse as Response)
      ).rejects.toThrow('User not authenticated');
    });

    it('should handle user not found', async () => {
      mockRequest.user = mockUser;
      userService.getUserById = jest.fn().mockResolvedValue(null);

      await expect(
        authController.getCurrentUser(mockRequest as any, mockResponse as Response)
      ).rejects.toThrow('User not found');
    });

    it('should handle service errors', async () => {
      mockRequest.user = mockUser;
      const serviceError = new Error('Database connection failed');
      userService.getUserById = jest.fn().mockRejectedValue(serviceError);

      await expect(
        authController.getCurrentUser(mockRequest as any, mockResponse as Response)
      ).rejects.toThrow('Database connection failed');
    });
  });

  describe('rate limiting helpers', () => {
    it('should reset rate limiting after lockout period', async () => {
      const authError = new AuthenticationError('Invalid credentials');
      authService.authenticateUser = jest.fn().mockRejectedValue(authError);

      // Make 5 failed attempts
      for (let i = 0; i < 5; i++) {
        try {
          await authController.login(mockRequest as Request, mockResponse as Response);
        } catch (error) {
          // Expected to fail
        }
      }

      // Mock time passage (15 minutes + 1ms)
      const originalDate = Date.now;
      Date.now = jest.fn().mockReturnValue(originalDate() + 15 * 60 * 1000 + 1);

      // Should not be rate limited anymore
      const user = mockUser;
      const mockTokens = { accessToken: 'access-token', refreshToken: 'refresh-token' };
      authService.authenticateUser = jest.fn().mockResolvedValue(user);
      authService.generateTokens = jest.fn().mockReturnValue(mockTokens);

      await expect(
        authController.login(mockRequest as Request, mockResponse as Response)
      ).resolves.not.toThrow();

      // Restore original Date.now
      Date.now = originalDate;
    });

    it('should track failed attempts per IP address', async () => {
      const authError = new AuthenticationError('Invalid credentials');
      authService.authenticateUser = jest.fn().mockRejectedValue(authError);

      // Simulate failed attempts from different IPs
      const ip1Request = { ...mockRequest, ip: '192.168.1.1' };
      const ip2Request = { ...mockRequest, ip: '192.168.1.2' };

      // IP1 makes 3 failed attempts
      for (let i = 0; i < 3; i++) {
        try {
          await authController.login(ip1Request as Request, mockResponse as Response);
        } catch (error) {
          // Expected to fail
        }
      }

      // IP2 makes 5 failed attempts (should be locked out)
      for (let i = 0; i < 5; i++) {
        try {
          await authController.login(ip2Request as Request, mockResponse as Response);
        } catch (error) {
          // Expected to fail
        }
      }

      // IP1 should still be able to attempt (not locked out)
      try {
        await authController.login(ip1Request as Request, mockResponse as Response);
      } catch (error) {
        expect(error).not.toMatch(/Too many login attempts/);
      }

      // IP2 should be rate limited
      await expect(
        authController.login(ip2Request as Request, mockResponse as Response)
      ).rejects.toThrow(/Too many login attempts/);
    });
  });
});