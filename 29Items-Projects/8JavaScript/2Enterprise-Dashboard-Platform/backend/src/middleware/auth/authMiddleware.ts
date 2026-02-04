import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';
import { Request, Response, NextFunction } from 'express';
import { z } from 'zod';
import { prisma } from '@/config/database.js';
import { cacheService } from '@/config/redis.js';
import { config } from '@/config/environment.js';
import { AuthenticationError, AuthorizationError, ValidationError } from '@/utils/errors.js';
import { logAuthEvent, logSecurityEvent } from '@/utils/logger.js';
import type { User, UserRole } from '@prisma/client';

// Extend Express Request interface
export interface AuthenticatedRequest extends Request {
  user?: User;
  token?: string;
}

// JWT payload interface
interface JWTPayload {
  userId: string;
  email: string;
  role: UserRole;
  iat?: number;
  exp?: number;
}

// Token pair interface
interface TokenPair {
  accessToken: string;
  refreshToken: string;
}

// Authentication service class
export class AuthService {
  // Generate JWT tokens
  public generateTokens(user: User): TokenPair {
    const payload: Omit<JWTPayload, 'iat' | 'exp'> = {
      userId: user.id,
      email: user.email,
      role: user.role,
    };

    const accessToken = jwt.sign(payload, config.auth.jwtSecret, {
      expiresIn: config.auth.jwtExpiry as any,
      issuer: config.app.name,
      audience: config.app.url,
    });

    const refreshToken = jwt.sign(payload, config.auth.jwtSecret, {
      expiresIn: config.auth.jwtRefreshExpiry as any,
      issuer: config.app.name,
      audience: config.app.url,
    });

    return { accessToken, refreshToken };
  }

  // Verify JWT token
  public verifyToken(token: string): JWTPayload {
    try {
      const decoded = jwt.verify(token, config.auth.jwtSecret, {
        issuer: config.app.name,
        audience: config.app.url,
      }) as JWTPayload;

      return decoded;
    } catch (error) {
      if (error instanceof jwt.TokenExpiredError) {
        throw new AuthenticationError('Token has expired');
      } else if (error instanceof jwt.JsonWebTokenError) {
        throw new AuthenticationError('Invalid token');
      } else {
        throw new AuthenticationError('Token verification failed');
      }
    }
  }

  // Hash password
  public async hashPassword(password: string): Promise<string> {
    return await bcrypt.hash(password, config.auth.bcryptRounds);
  }

  // Verify password
  public async verifyPassword(password: string, hashedPassword: string): Promise<boolean> {
    return await bcrypt.compare(password, hashedPassword);
  }

  // Authenticate user with email and password
  public async authenticateUser(email: string, password: string): Promise<User> {
    // Find user by email
    const user = await prisma.user.findUnique({
      where: { email: email.toLowerCase() },
      include: { profile: true },
    });

    if (!user) {
      logSecurityEvent('FAILED_LOGIN_ATTEMPT', 'medium', {
        email,
        reason: 'User not found',
      });
      throw new AuthenticationError('Invalid credentials');
    }

    // Check if user is active
    if (!user.isActive) {
      logSecurityEvent('FAILED_LOGIN_ATTEMPT', 'high', {
        userId: user.id,
        email,
        reason: 'Account deactivated',
      });
      throw new AuthenticationError('Account is deactivated');
    }

    // Verify password
    const isValidPassword = await this.verifyPassword(password, user.password);
    if (!isValidPassword) {
      logSecurityEvent('FAILED_LOGIN_ATTEMPT', 'medium', {
        userId: user.id,
        email,
        reason: 'Invalid password',
      });
      throw new AuthenticationError('Invalid credentials');
    }

    // Update last login
    await prisma.user.update({
      where: { id: user.id },
      data: { lastLogin: new Date() },
    });

    logAuthEvent('USER_LOGIN_SUCCESS', user.id, { email });

    return user;
  }

  // Register new user
  public async registerUser(data: {
    email: string;
    password: string;
    firstName?: string;
    lastName?: string;
  }): Promise<User> {
    const { email, password, firstName, lastName } = data;

    // Check if user already exists
    const existingUser = await prisma.user.findUnique({
      where: { email: email.toLowerCase() },
    });

    if (existingUser) {
      throw new ValidationError('User with this email already exists');
    }

    // Hash password
    const hashedPassword = await this.hashPassword(password);

    // Create user with profile
    const user = await prisma.user.create({
      data: {
        email: email.toLowerCase(),
        password: hashedPassword,
        firstName,
        lastName,
        profile: {
          create: {
            theme: 'light',
            timezone: 'UTC',
            language: 'en',
            notifications: {},
          },
        },
      },
      include: { profile: true },
    });

    logAuthEvent('USER_REGISTRATION_SUCCESS', user.id, {
      email: user.email,
    });

    return user;
  }

  // Get user from token
  public async getUserFromToken(token: string): Promise<User> {
    // Check if token is blacklisted (for logout functionality)
    const isBlacklisted = await cacheService.exists(`blacklisted_token:${token}`);
    if (isBlacklisted) {
      throw new AuthenticationError('Token has been invalidated');
    }

    // Verify token
    const payload = this.verifyToken(token);

    // Try to get user from cache first
    const cacheKey = `user:${payload.userId}`;
    let user = await cacheService.get<User>(cacheKey);

    if (!user) {
      // Get user from database
      user = await prisma.user.findUnique({
        where: { id: payload.userId },
        include: { profile: true },
      });

      if (!user) {
        throw new AuthenticationError('User not found');
      }

      if (!user.isActive) {
        throw new AuthenticationError('Account is deactivated');
      }

      // Cache user for 5 minutes
      await cacheService.set(cacheKey, user, 300);
    }

    return user;
  }

  // Refresh tokens
  public async refreshTokens(refreshToken: string): Promise<TokenPair> {
    const payload = this.verifyToken(refreshToken);

    const user = await prisma.user.findUnique({
      where: { id: payload.userId },
      include: { profile: true },
    });

    if (!user || !user.isActive) {
      throw new AuthenticationError('Invalid refresh token');
    }

    return this.generateTokens(user);
  }

  // Logout (blacklist token)
  public async logout(token: string): Promise<void> {
    const payload = this.verifyToken(token);

    // Calculate remaining TTL for the token
    const now = Math.floor(Date.now() / 1000);
    const tokenTTL = payload.exp ? payload.exp - now : 3600; // Default 1 hour

    if (tokenTTL > 0) {
      // Blacklist token for remaining TTL
      await cacheService.set(`blacklisted_token:${token}`, true, tokenTTL);
    }

    logAuthEvent('USER_LOGOUT', payload.userId);
  }

  // Change password
  public async changePassword(
    userId: string,
    currentPassword: string,
    newPassword: string
  ): Promise<void> {
    const user = await prisma.user.findUnique({
      where: { id: userId },
    });

    if (!user) {
      throw new AuthenticationError('User not found');
    }

    // Verify current password
    const isValidPassword = await this.verifyPassword(currentPassword, user.password);
    if (!isValidPassword) {
      throw new AuthenticationError('Current password is incorrect');
    }

    // Hash new password
    const hashedNewPassword = await this.hashPassword(newPassword);

    // Update password
    await prisma.user.update({
      where: { id: userId },
      data: { password: hashedNewPassword },
    });

    // Clear user from cache
    await cacheService.delete(`user:${userId}`);

    logAuthEvent('PASSWORD_CHANGED', userId);
  }

  // Reset password (would typically involve email verification)
  public async initiatePasswordReset(email: string): Promise<void> {
    const user = await prisma.user.findUnique({
      where: { email: email.toLowerCase() },
    });

    if (!user) {
      // Don't reveal whether user exists or not
      return;
    }

    // Generate reset token (in production, this would be sent via email)
    const resetToken = jwt.sign(
      { userId: user.id, purpose: 'password_reset' },
      config.auth.jwtSecret,
      { expiresIn: '1h' }
    );

    // Store reset token in cache for 1 hour
    await cacheService.set(`password_reset:${user.id}`, resetToken, 3600);

    logAuthEvent('PASSWORD_RESET_INITIATED', user.id, { email });
  }

  // Complete password reset
  public async resetPassword(resetToken: string, newPassword: string): Promise<void> {
    try {
      const payload = jwt.verify(resetToken, config.auth.jwtSecret) as any;

      if (payload.purpose !== 'password_reset') {
        throw new AuthenticationError('Invalid reset token');
      }

      // Check if token exists in cache
      const cachedToken = await cacheService.get(`password_reset:${payload.userId}`);
      if (!cachedToken || cachedToken !== resetToken) {
        throw new AuthenticationError('Reset token has expired or been used');
      }

      // Hash new password
      const hashedPassword = await this.hashPassword(newPassword);

      // Update password
      await prisma.user.update({
        where: { id: payload.userId },
        data: { password: hashedPassword },
      });

      // Remove reset token from cache
      await cacheService.delete(`password_reset:${payload.userId}`);

      // Clear user from cache
      await cacheService.delete(`user:${payload.userId}`);

      logAuthEvent('PASSWORD_RESET_COMPLETED', payload.userId);

    } catch (error) {
      if (error instanceof jwt.TokenExpiredError) {
        throw new AuthenticationError('Reset token has expired');
      } else if (error instanceof jwt.JsonWebTokenError) {
        throw new AuthenticationError('Invalid reset token');
      }
      throw error;
    }
  }
}

// Create auth service instance
export const authService = new AuthService();

// Authentication middleware
export const authenticate = async (
  req: AuthenticatedRequest,
  res: Response,
  next: NextFunction
): Promise<void> => {
  try {
    const authHeader = req.headers.authorization;

    if (!authHeader) {
      throw new AuthenticationError('Authorization header is required');
    }

    const parts = authHeader.split(' ');
    if (parts.length !== 2 || parts[0] !== 'Bearer') {
      throw new AuthenticationError('Invalid authorization header format');
    }

    const token = parts[1]!;
    const user = await authService.getUserFromToken(token);

    req.user = user;
    req.token = token;

    next();
  } catch (error) {
    next(error);
  }
};

// Optional authentication (doesn't throw error if no token)
export const optionalAuthenticate = async (
  req: AuthenticatedRequest,
  res: Response,
  next: NextFunction
): Promise<void> => {
  try {
    const authHeader = req.headers.authorization;

    if (authHeader) {
      const parts = authHeader.split(' ');
      if (parts.length === 2 && parts[0] === 'Bearer') {
        const token = parts[1]!;
        const user = await authService.getUserFromToken(token);
        req.user = user;
        req.token = token;
      }
    }

    next();
  } catch (error) {
    // Continue without authentication for optional auth
    next();
  }
};

// Authorization middleware (check user roles)
export const authorize = (...roles: UserRole[]) => {
  return (req: AuthenticatedRequest, res: Response, next: NextFunction): void => {
    if (!req.user) {
      return next(new AuthenticationError('User not authenticated'));
    }

    if (roles.length > 0 && !roles.includes(req.user.role)) {
      logSecurityEvent('UNAUTHORIZED_ACCESS_ATTEMPT', 'medium', {
        userId: req.user.id,
        userRole: req.user.role,
        requiredRoles: roles,
        path: req.path,
        method: req.method,
      });

      return next(new AuthorizationError('Insufficient permissions'));
    }

    next();
  };
};

// Resource owner check (user can only access their own resources)
export const requireOwnership = (resourceUserIdField: string = 'userId') => {
  return (req: AuthenticatedRequest, res: Response, next: NextFunction): void => {
    if (!req.user) {
      return next(new AuthenticationError('User not authenticated'));
    }

    const resourceUserId = req.params[resourceUserIdField] || req.body[resourceUserIdField];

    if (req.user.role === 'SUPER_ADMIN' || req.user.role === 'ADMIN') {
      // Admins can access any resource
      return next();
    }

    if (resourceUserId && resourceUserId !== req.user.id) {
      logSecurityEvent('UNAUTHORIZED_RESOURCE_ACCESS', 'medium', {
        userId: req.user.id,
        attemptedResourceUserId: resourceUserId,
        path: req.path,
        method: req.method,
      });

      return next(new AuthorizationError('Access denied to this resource'));
    }

    next();
  };
};

// Rate limiting by user
export const userRateLimit = (maxRequests: number = 100, windowMs: number = 15 * 60 * 1000) => {
  return async (req: AuthenticatedRequest, res: Response, next: NextFunction): Promise<void> => {
    if (!req.user) {
      return next();
    }

    const key = `rate_limit:user:${req.user.id}`;
    const windowStart = Math.floor(Date.now() / windowMs) * windowMs;
    const windowKey = `${key}:${windowStart}`;

    try {
      const current = await cacheService.get<number>(windowKey) || 0;

      if (current >= maxRequests) {
        throw new AuthenticationError('Rate limit exceeded');
      }

      await cacheService.increment(windowKey);
      await cacheService.expire(windowKey, Math.ceil(windowMs / 1000));

      next();
    } catch (error) {
      next(error);
    }
  };
};

// Validation schemas for auth endpoints
export const authSchemas = {
  login: z.object({
    email: z.string().email('Invalid email format'),
    password: z.string().min(1, 'Password is required'),
  }),

  register: z.object({
    email: z.string().email('Invalid email format'),
    password: z.string()
      .min(8, 'Password must be at least 8 characters long')
      .regex(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/, 'Password must contain at least one lowercase letter, one uppercase letter, and one number'),
    firstName: z.string().optional(),
    lastName: z.string().optional(),
  }),

  changePassword: z.object({
    currentPassword: z.string().min(1, 'Current password is required'),
    newPassword: z.string()
      .min(8, 'New password must be at least 8 characters long')
      .regex(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/, 'New password must contain at least one lowercase letter, one uppercase letter, and one number'),
  }),

  resetPassword: z.object({
    email: z.string().email('Invalid email format'),
  }),

  confirmResetPassword: z.object({
    token: z.string().min(1, 'Reset token is required'),
    newPassword: z.string()
      .min(8, 'New password must be at least 8 characters long')
      .regex(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/, 'New password must contain at least one lowercase letter, one uppercase letter, and one number'),
  }),

  refreshToken: z.object({
    refreshToken: z.string().min(1, 'Refresh token is required'),
  }),
};