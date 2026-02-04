import { Request, Response } from 'express';
import { z } from 'zod';
import { authService, authSchemas, AuthenticatedRequest } from '@/middleware/auth/authMiddleware.js';
import { userService } from '@/services/user/userService.js';
import { logger, logAuthEvent, logSecurityEvent } from '@/utils/logger.js';
import { AppError, ValidationError, AuthenticationError } from '@/utils/errors.js';
import { asyncHandler } from '@/utils/asyncHandler.js';

// Rate limiting tracking
const loginAttempts = new Map<string, { count: number; lastAttempt: number }>();
const MAX_LOGIN_ATTEMPTS = 5;
const LOCKOUT_DURATION = 15 * 60 * 1000; // 15 minutes

export class AuthController {
  /**
   * User registration
   * POST /api/auth/register
   */
  register = asyncHandler(async (req: Request, res: Response) => {
    // Validate request body
    const validatedData = authSchemas.register.parse(req.body);
    const { email, password, firstName, lastName } = validatedData;

    // Get client IP for security logging
    const clientIP = req.ip || req.connection.remoteAddress;

    try {
      // Create user
      const user = await authService.registerUser({
        email,
        password,
        firstName,
        lastName,
      });

      // Generate tokens
      const tokens = authService.generateTokens(user);

      // Log successful registration
      logAuthEvent('USER_REGISTRATION', user.id, {
        email: user.email,
        ipAddress: clientIP,
        userAgent: req.get('user-agent'),
      });

      logger.info('User registered successfully', {
        userId: user.id,
        email: user.email,
        ipAddress: clientIP,
      });

      // Remove sensitive data from response
      const userResponse = {
        id: user.id,
        email: user.email,
        firstName: user.firstName,
        lastName: user.lastName,
        role: user.role,
        isActive: user.isActive,
        createdAt: user.createdAt,
        profile: (user as any).profile,
      };

      res.status(201).json({
        success: true,
        data: {
          user: userResponse,
          tokens,
        },
        message: 'User registered successfully',
      });
    } catch (error) {
      logSecurityEvent('REGISTRATION_FAILED', 'medium', {
        email,
        ipAddress: clientIP,
        error: error instanceof Error ? error.message : 'Unknown error',
      });

      logger.error('User registration failed', {
        email,
        ipAddress: clientIP,
        error,
      });

      throw error;
    }
  });

  /**
   * User login
   * POST /api/auth/login
   */
  login = asyncHandler(async (req: Request, res: Response) => {
    // Validate request body
    const validatedData = authSchemas.login.parse(req.body);
    const { email, password } = validatedData;

    const clientIP = req.ip || req.connection.remoteAddress || 'unknown';
    const userAgent = req.get('user-agent') || 'unknown';

    try {
      // Check rate limiting
      this.checkLoginRateLimit(clientIP);

      // Authenticate user
      const user = await authService.authenticateUser(email, password);

      // Clear failed attempts on successful login
      loginAttempts.delete(clientIP);

      // Generate tokens
      const tokens = authService.generateTokens(user);

      // Log successful login
      logAuthEvent('USER_LOGIN', user.id, {
        email: user.email,
        ipAddress: clientIP,
        userAgent,
      });

      logger.info('User logged in successfully', {
        userId: user.id,
        email: user.email,
        ipAddress: clientIP,
      });

      // Remove sensitive data from response
      const userResponse = {
        id: user.id,
        email: user.email,
        firstName: user.firstName,
        lastName: user.lastName,
        role: user.role,
        isActive: user.isActive,
        lastLogin: user.lastLogin,
        profile: (user as any).profile,
      };

      res.json({
        success: true,
        data: {
          user: userResponse,
          tokens,
        },
        message: 'Login successful',
      });
    } catch (error) {
      // Track failed login attempts
      this.trackFailedLogin(clientIP);

      logSecurityEvent('LOGIN_FAILED', 'medium', {
        email,
        ipAddress: clientIP,
        userAgent,
        error: error instanceof Error ? error.message : 'Unknown error',
      });

      logger.warn('User login failed', {
        email,
        ipAddress: clientIP,
        error: error instanceof Error ? error.message : 'Unknown error',
      });

      throw error;
    }
  });

  /**
   * Refresh tokens
   * POST /api/auth/refresh
   */
  refresh = asyncHandler(async (req: Request, res: Response) => {
    // Validate request body
    const validatedData = authSchemas.refreshToken.parse(req.body);
    const { refreshToken } = validatedData;

    const clientIP = req.ip || req.connection.remoteAddress || 'unknown';

    try {
      const tokens = await authService.refreshTokens(refreshToken);

      logger.info('Tokens refreshed successfully', {
        ipAddress: clientIP,
      });

      res.json({
        success: true,
        data: { tokens },
        message: 'Tokens refreshed successfully',
      });
    } catch (error) {
      logSecurityEvent('TOKEN_REFRESH_FAILED', 'medium', {
        ipAddress: clientIP,
        error: error instanceof Error ? error.message : 'Unknown error',
      });

      logger.warn('Token refresh failed', {
        ipAddress: clientIP,
        error: error instanceof Error ? error.message : 'Unknown error',
      });

      throw error;
    }
  });

  /**
   * User logout
   * POST /api/auth/logout
   */
  logout = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id;
    const token = req.token;

    if (!userId || !token) {
      throw new AuthenticationError('User not authenticated');
    }

    const clientIP = req.ip || req.connection.remoteAddress || 'unknown';

    try {
      await authService.logout(token);

      logAuthEvent('USER_LOGOUT', userId, {
        ipAddress: clientIP,
      });

      logger.info('User logged out successfully', {
        userId,
        ipAddress: clientIP,
      });

      res.json({
        success: true,
        message: 'Logout successful',
      });
    } catch (error) {
      logger.error('Logout failed', {
        userId,
        ipAddress: clientIP,
        error,
      });

      throw error;
    }
  });

  /**
   * Change password
   * POST /api/auth/change-password
   */
  changePassword = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id;

    if (!userId) {
      throw new AuthenticationError('User not authenticated');
    }

    // Validate request body
    const validatedData = authSchemas.changePassword.parse(req.body);
    const { currentPassword, newPassword } = validatedData;

    const clientIP = req.ip || req.connection.remoteAddress || 'unknown';

    try {
      await authService.changePassword(userId, currentPassword, newPassword);

      logAuthEvent('PASSWORD_CHANGED', userId, {
        ipAddress: clientIP,
      });

      logger.info('Password changed successfully', {
        userId,
        ipAddress: clientIP,
      });

      res.json({
        success: true,
        message: 'Password changed successfully',
      });
    } catch (error) {
      logSecurityEvent('PASSWORD_CHANGE_FAILED', 'medium', {
        userId,
        ipAddress: clientIP,
        error: error instanceof Error ? error.message : 'Unknown error',
      });

      logger.error('Password change failed', {
        userId,
        ipAddress: clientIP,
        error,
      });

      throw error;
    }
  });

  /**
   * Initiate password reset
   * POST /api/auth/forgot-password
   */
  forgotPassword = asyncHandler(async (req: Request, res: Response) => {
    // Validate request body
    const validatedData = authSchemas.resetPassword.parse(req.body);
    const { email } = validatedData;

    const clientIP = req.ip || req.connection.remoteAddress || 'unknown';

    try {
      await authService.initiatePasswordReset(email);

      // Always return success to prevent email enumeration
      logger.info('Password reset initiated', {
        email,
        ipAddress: clientIP,
      });

      res.json({
        success: true,
        message: 'If an account with that email exists, a password reset link has been sent',
      });
    } catch (error) {
      logger.error('Password reset initiation failed', {
        email,
        ipAddress: clientIP,
        error,
      });

      // Still return success to prevent email enumeration
      res.json({
        success: true,
        message: 'If an account with that email exists, a password reset link has been sent',
      });
    }
  });

  /**
   * Complete password reset
   * POST /api/auth/reset-password
   */
  resetPassword = asyncHandler(async (req: Request, res: Response) => {
    // Validate request body
    const validatedData = authSchemas.confirmResetPassword.parse(req.body);
    const { token, newPassword } = validatedData;

    const clientIP = req.ip || req.connection.remoteAddress || 'unknown';

    try {
      await authService.resetPassword(token, newPassword);

      logger.info('Password reset completed', {
        ipAddress: clientIP,
      });

      res.json({
        success: true,
        message: 'Password reset successfully',
      });
    } catch (error) {
      logSecurityEvent('PASSWORD_RESET_FAILED', 'medium', {
        ipAddress: clientIP,
        error: error instanceof Error ? error.message : 'Unknown error',
      });

      logger.error('Password reset failed', {
        ipAddress: clientIP,
        error,
      });

      throw error;
    }
  });

  /**
   * Verify token (check if token is valid)
   * POST /api/auth/verify
   */
  verifyToken = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const user = req.user;

    if (!user) {
      throw new AuthenticationError('Invalid token');
    }

    // Remove sensitive data
    const userResponse = {
      id: user.id,
      email: user.email,
      firstName: user.firstName,
      lastName: user.lastName,
      role: user.role,
      isActive: user.isActive,
      profile: (user as any).profile,
    };

    res.json({
      success: true,
      data: { user: userResponse },
      message: 'Token is valid',
    });
  });

  /**
   * Get current user profile
   * GET /api/auth/me
   */
  getCurrentUser = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id;

    if (!userId) {
      throw new AuthenticationError('User not authenticated');
    }

    try {
      const user = await userService.getUserById(userId, true);

      if (!user) {
        throw new AuthenticationError('User not found');
      }

      res.json({
        success: true,
        data: { user },
        message: 'User profile retrieved successfully',
      });
    } catch (error) {
      logger.error('Failed to get current user', {
        userId,
        error,
      });

      throw error;
    }
  });

  // Helper methods
  private checkLoginRateLimit(ip: string): void {
    const attempt = loginAttempts.get(ip);
    const now = Date.now();

    if (attempt) {
      // Reset counter if lockout period has passed
      if (now - attempt.lastAttempt > LOCKOUT_DURATION) {
        loginAttempts.delete(ip);
        return;
      }

      // Check if locked out
      if (attempt.count >= MAX_LOGIN_ATTEMPTS) {
        const remainingTime = Math.ceil((LOCKOUT_DURATION - (now - attempt.lastAttempt)) / 1000 / 60);

        logSecurityEvent('LOGIN_RATE_LIMIT_EXCEEDED', 'high', {
          ipAddress: ip,
          attempts: attempt.count,
          remainingLockoutMinutes: remainingTime,
        });

        throw new AppError(
          `Too many login attempts. Please try again in ${remainingTime} minutes.`,
          429,
          'RATE_LIMIT_EXCEEDED'
        );
      }
    }
  }

  private trackFailedLogin(ip: string): void {
    const attempt = loginAttempts.get(ip);
    const now = Date.now();

    if (attempt) {
      // Reset if it's been more than lockout duration
      if (now - attempt.lastAttempt > LOCKOUT_DURATION) {
        loginAttempts.set(ip, { count: 1, lastAttempt: now });
      } else {
        loginAttempts.set(ip, { count: attempt.count + 1, lastAttempt: now });
      }
    } else {
      loginAttempts.set(ip, { count: 1, lastAttempt: now });
    }
  }
}

// Create controller instance
export const authController = new AuthController();

// Export individual methods for use in routes
export const {
  register,
  login,
  refresh,
  logout,
  changePassword,
  forgotPassword,
  resetPassword,
  verifyToken,
  getCurrentUser,
} = authController;