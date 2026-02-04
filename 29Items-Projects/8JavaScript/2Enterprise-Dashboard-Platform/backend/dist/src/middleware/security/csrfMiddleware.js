import crypto from 'crypto';
import { cacheService } from '@/config/redis.js';
import { config } from '@/config/environment.js';
import { ValidationError } from '@/utils/errors.js';
import { logger } from '@/utils/logger.js';
const CSRF_TOKEN_LENGTH = 32;
const CSRF_TOKEN_TTL = 3600;
const CSRF_HEADER_NAME = 'x-csrf-token';
const CSRF_COOKIE_NAME = 'csrf-token';
class CSRFTokenService {
    generateToken() {
        return crypto.randomBytes(CSRF_TOKEN_LENGTH).toString('hex');
    }
    getTokenKey(sessionId, token) {
        return `csrf:${sessionId}:${token}`;
    }
    async storeToken(sessionId, token) {
        const key = this.getTokenKey(sessionId, token);
        await cacheService.set(key, true, CSRF_TOKEN_TTL);
        logger.debug('CSRF token stored', {
            sessionId,
            tokenLength: token.length,
            ttl: CSRF_TOKEN_TTL,
        });
    }
    async validateAndConsumeToken(sessionId, token) {
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
        await cacheService.delete(key);
        logger.debug('CSRF token validated and consumed', {
            sessionId,
        });
        return true;
    }
    async cleanupExpiredTokens(sessionId) {
        const pattern = `csrf:${sessionId}:*`;
        const keys = await cacheService.keys(pattern);
        if (keys.length > 10) {
            await cacheService.deletePattern(pattern);
            logger.info('Cleaned up excess CSRF tokens', {
                sessionId,
                count: keys.length,
            });
        }
    }
}
export const csrfTokenService = new CSRFTokenService();
function getSessionId(req) {
    if (req.user?.id) {
        return `user:${req.user.id}`;
    }
    if (req.sessionID) {
        return `session:${req.sessionID}`;
    }
    const ip = req.ip || req.connection.remoteAddress || 'unknown';
    return `ip:${ip}`;
}
export const generateCSRFToken = async (req, res) => {
    try {
        const sessionId = getSessionId(req);
        const token = csrfTokenService.generateToken();
        await csrfTokenService.storeToken(sessionId, token);
        await csrfTokenService.cleanupExpiredTokens(sessionId);
        res.cookie(CSRF_COOKIE_NAME, token, {
            httpOnly: false,
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
    }
    catch (error) {
        logger.error('CSRF token generation failed', {
            error: error instanceof Error ? error.message : 'Unknown error',
            sessionId: getSessionId(req),
        });
        throw new ValidationError('Failed to generate CSRF token');
    }
};
export const csrfProtection = async (req, res, next) => {
    try {
        const safeMethods = ['GET', 'HEAD', 'OPTIONS'];
        if (safeMethods.includes(req.method)) {
            return next();
        }
        if (config.app.env === 'development' && !config.security.enableCsrf) {
            logger.debug('CSRF protection skipped in development');
            return next();
        }
        const sessionId = getSessionId(req);
        let token = req.headers[CSRF_HEADER_NAME];
        if (!token && req.body && req.body._csrf) {
            token = req.body._csrf;
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
        req.csrfToken = token;
        logger.debug('CSRF protection passed', {
            method: req.method,
            path: req.path,
            sessionId,
        });
        next();
    }
    catch (error) {
        next(error);
    }
};
export const conditionalCSRFProtection = async (req, res, next) => {
    try {
        if (req.user) {
            return csrfProtection(req, res, next);
        }
        next();
    }
    catch (error) {
        next(error);
    }
};
export const doubleSubmitCookieValidation = (req, res, next) => {
    try {
        const safeMethods = ['GET', 'HEAD', 'OPTIONS'];
        if (safeMethods.includes(req.method)) {
            return next();
        }
        const cookieToken = req.cookies[CSRF_COOKIE_NAME];
        const headerToken = req.headers[CSRF_HEADER_NAME];
        if (!cookieToken || !headerToken) {
            throw new ValidationError('CSRF tokens are required');
        }
        if (!crypto.timingSafeEqual(Buffer.from(cookieToken), Buffer.from(headerToken))) {
            logger.warn('Double-submit cookie validation failed', {
                method: req.method,
                path: req.path,
                ip: req.ip,
            });
            throw new ValidationError('CSRF token mismatch');
        }
        next();
    }
    catch (error) {
        next(error);
    }
};
export const refreshCSRFToken = async (req, res) => {
    try {
        const sessionId = getSessionId(req);
        await csrfTokenService.cleanupExpiredTokens(sessionId);
        await generateCSRFToken(req, res);
    }
    catch (error) {
        logger.error('CSRF token refresh failed', {
            error: error instanceof Error ? error.message : 'Unknown error',
            sessionId: getSessionId(req),
        });
        throw new ValidationError('Failed to refresh CSRF token');
    }
};
export const apiCSRFProtection = async (req, res, next) => {
    try {
        const safeMethods = ['GET', 'HEAD', 'OPTIONS'];
        if (safeMethods.includes(req.method)) {
            return next();
        }
        const apiKey = req.headers['x-api-key'];
        const serviceAuth = req.headers['x-service-auth'];
        if (apiKey || serviceAuth) {
            logger.debug('CSRF protection skipped for API/service request');
            return next();
        }
        return csrfProtection(req, res, next);
    }
    catch (error) {
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
//# sourceMappingURL=csrfMiddleware.js.map