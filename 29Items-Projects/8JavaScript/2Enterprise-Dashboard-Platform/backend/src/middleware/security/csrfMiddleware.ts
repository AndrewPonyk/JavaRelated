import { Request, Response, NextFunction } from 'express';
import crypto from 'crypto';
import { cacheService } from '@/config/redis.js';
import { config } from '@/config/environment.js';
import { AuthenticationError, ValidationError } from '@/utils/errors.js';
import { logger } from '@/utils/logger.js';
import type { AuthenticatedRequest } from '@/middleware/auth/authMiddleware.js';

// CSRF token configuration
const CSRF_TOKEN_LENGTH = 32;
const CSRF_TOKEN_TTL = 3600; // 1 hour
const CSRF_HEADER_NAME = 'x-csrf-token';
const CSRF_COOKIE_NAME = 'csrf-token';

// Interface for CSRF-enabled request
export interface CSRFRequest extends AuthenticatedRequest {
  csrfToken?: string;
}

// CSRF token utilities
class CSRFTokenService {
  /**
   * Generate a cryptographically secure CSRF token
   */
  public generateToken(): string {
    return crypto.randomBytes(CSRF_TOKEN_LENGTH).toString('hex');
  }

  /**
   * Create CSRF token key for cache storage
   */
  private getTokenKey(sessionId: string, token: string): string {
    return `csrf:${sessionId}:${token}`;
  }

  /**
   * Store CSRF token in cache
   */
  public async storeToken(sessionId: string, token: string): Promise<void> {
    const key = this.getTokenKey(sessionId, token);
    await cacheService.set(key, true, CSRF_TOKEN_TTL);

    logger.debug('CSRF token stored', {
      sessionId,
      tokenLength: token.length,
      ttl: CSRF_TOKEN_TTL,
    });
  }

  /**
   * Validate and consume CSRF token
   */
  public async validateAndConsumeToken(sessionId: string, token: string): Promise<boolean> {
    if (!token || !sessionId) {
      return false;
    }

    const key = this.getTokenKey(sessionId, token);
    const exists = await cacheService.exists(key);

    if (!exists) {
      logger.warn('CSRF token validation failed', {
        sessionId,
        reason: 'Token not found or expired',
      });
      return false;
    }

    // Remove token after use (one-time use)
    await cacheService.delete(key);

    logger.debug('CSRF token validated and consumed', {
      sessionId,
    });

    return true;
  }

  /**
   * Clean up expired tokens for a session
   */
  public async cleanupExpiredTokens(sessionId: string): Promise<void> {
    const pattern = `csrf:${sessionId}:*`;
    const keys = await cacheService.keys(pattern);

    if (keys.length > 10) { // Clean up if too many tokens
      await cacheService.deletePattern(pattern);
      logger.info('Cleaned up excess CSRF tokens', {
        sessionId,
        count: keys.length,
      });
    }
  }
}

// Create service instance
export const csrfTokenService = new CSRFTokenService();

/**
 * Get session ID for CSRF token management
 * Uses user ID for authenticated requests, or session from cookies
 */
function getSessionId(req: CSRFRequest): string {
  // For authenticated requests, use user ID
  if (req.user?.id) {
    return `user:${req.user.id}`;
  }

  // For unauthenticated requests, use session ID from cookies
  if (req.sessionID) {
    return `session:${req.sessionID}`;
  }

  // Fallback to IP address (less secure but works)
  const ip = req.ip || req.connection.remoteAddress || 'unknown';
  return `ip:${ip}`;
}

/**
 * CSRF token generation endpoint
 * GET /api/auth/csrf-token
 */
export const generateCSRFToken = async (req: CSRFRequest, res: Response): Promise<void> => {
  try {
    const sessionId = getSessionId(req);
    const token = csrfTokenService.generateToken();

    // Store token in cache
    await csrfTokenService.storeToken(sessionId, token);

    // Clean up old tokens periodically
    await csrfTokenService.cleanupExpiredTokens(sessionId);

    // Set secure cookie with token
    res.cookie(CSRF_COOKIE_NAME, token, {
      httpOnly: false, // Allow JavaScript access for AJAX requests
      secure: config.app.env === 'production',
      sameSite: 'strict',
      maxAge: CSRF_TOKEN_TTL * 1000,
    });

    logger.info('CSRF token generated', {
      sessionId,
      userAgent: req.get('user-agent'),
      ip: req.ip,
    });

    res.json({
      success: true,
      data: {
        token,
        expiresAt: new Date(Date.now() + CSRF_TOKEN_TTL * 1000).toISOString(),
      },
      message: 'CSRF token generated successfully',
    });
  } catch (error) {
    logger.error('CSRF token generation failed', {
      error: error instanceof Error ? error.message : 'Unknown error',
      sessionId: getSessionId(req),
    });

    throw new ValidationError('Failed to generate CSRF token');
  }
};

/**
 * CSRF protection middleware for state-changing operations
 */
export const csrfProtection = async (
  req: CSRFRequest,
  res: Response,
  next: NextFunction
): Promise<void> => {
  try {
    // Skip CSRF validation for GET, HEAD, OPTIONS requests
    const safeMethods = ['GET', 'HEAD', 'OPTIONS'];
    if (safeMethods.includes(req.method)) {
      return next();
    }

    // Skip CSRF validation in development if disabled
    if (config.app.env === 'development' && !config.security.enableCsrf) {
      logger.debug('CSRF protection skipped in development');
      return next();
    }

    const sessionId = getSessionId(req);

    // Extract CSRF token from header or body
    let token = req.headers[CSRF_HEADER_NAME] as string;

    if (!token && req.body && req.body._csrf) {
      token = req.body._csrf;
      // Remove token from body to prevent it from being processed
      delete req.body._csrf;
    }

    if (!token) {
      logger.warn('CSRF token missing', {
        method: req.method,
        path: req.path,
        sessionId,
        headers: Object.keys(req.headers),
      });

      throw new ValidationError('CSRF token is required');
    }

    // Validate and consume token
    const isValid = await csrfTokenService.validateAndConsumeToken(sessionId, token);

    if (!isValid) {
      logger.warn('CSRF validation failed', {
        method: req.method,
        path: req.path,
        sessionId,
        tokenLength: token?.length,
        ip: req.ip,
        userAgent: req.get('user-agent'),
      });

      throw new ValidationError('Invalid or expired CSRF token');
    }

    // Store token in request for potential use
    req.csrfToken = token;

    logger.debug('CSRF protection passed', {
      method: req.method,
      path: req.path,
      sessionId,
    });

    next();
  } catch (error) {
    next(error);
  }
};

/**
 * Conditional CSRF protection - only for authenticated state-changing operations
 */
export const conditionalCSRFProtection = async (
  req: CSRFRequest,
  res: Response,
  next: NextFunction
): Promise<void> => {
  try {
    // Only apply CSRF protection if user is authenticated
    if (req.user) {
      return csrfProtection(req, res, next);
    }

    // Skip for unauthenticated requests (like login, register)
    next();
  } catch (error) {
    next(error);
  }
};

/**
 * Double-submit cookie pattern validation
 * Validates that cookie token matches header token
 */
export const doubleSubmitCookieValidation = (
  req: CSRFRequest,
  res: Response,
  next: NextFunction
): void => {
  try {
    const safeMethods = ['GET', 'HEAD', 'OPTIONS'];
    if (safeMethods.includes(req.method)) {
      return next();
    }

    const cookieToken = req.cookies[CSRF_COOKIE_NAME];
    const headerToken = req.headers[CSRF_HEADER_NAME] as string;

    if (!cookieToken || !headerToken) {
      throw new ValidationError('CSRF tokens are required');
    }

    // Use constant-time comparison to prevent timing attacks
    if (!crypto.timingSafeEqual(Buffer.from(cookieToken), Buffer.from(headerToken))) {
      logger.warn('Double-submit cookie validation failed', {
        method: req.method,
        path: req.path,
        ip: req.ip,
      });

      throw new ValidationError('CSRF token mismatch');
    }

    next();
  } catch (error) {
    next(error);
  }
};

/**
 * Refresh CSRF token (generate new token while invalidating old one)
 */
export const refreshCSRFToken = async (req: CSRFRequest, res: Response): Promise<void> => {
  try {
    const sessionId = getSessionId(req);

    // Invalidate existing tokens
    await csrfTokenService.cleanupExpiredTokens(sessionId);

    // Generate new token
    await generateCSRFToken(req, res);
  } catch (error) {
    logger.error('CSRF token refresh failed', {
      error: error instanceof Error ? error.message : 'Unknown error',
      sessionId: getSessionId(req),
    });

    throw new ValidationError('Failed to refresh CSRF token');
  }
};

/**
 * CSRF token validation for API routes
 * More lenient for API usage with proper authentication
 */
export const apiCSRFProtection = async (
  req: CSRFRequest,
  res: Response,
  next: NextFunction
): Promise<void> => {
  try {
    // Skip for safe methods
    const safeMethods = ['GET', 'HEAD', 'OPTIONS'];
    if (safeMethods.includes(req.method)) {
      return next();
    }

    // If request has API key or service-to-service auth, skip CSRF
    const apiKey = req.headers['x-api-key'];
    const serviceAuth = req.headers['x-service-auth'];

    if (apiKey || serviceAuth) {
      logger.debug('CSRF protection skipped for API/service request');
      return next();
    }

    // Apply regular CSRF protection
    return csrfProtection(req, res, next);
  } catch (error) {
    next(error);
  }
};

export default {
  csrfProtection,
  conditionalCSRFProtection,
  doubleSubmitCookieValidation,
  apiCSRFProtection,
  generateCSRFToken,
  refreshCSRFToken,
  csrfTokenService,
};