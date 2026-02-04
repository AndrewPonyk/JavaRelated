import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';
import { z } from 'zod';
import { prisma } from '@/config/database.js';
import { cacheService } from '@/config/redis.js';
import { config } from '@/config/environment.js';
import { AuthenticationError, AuthorizationError, ValidationError } from '@/utils/errors.js';
import { logAuthEvent, logSecurityEvent } from '@/utils/logger.js';
export class AuthService {
    generateTokens(user) {
        const payload = {
            userId: user.id,
            email: user.email,
            role: user.role,
        };
        const accessToken = jwt.sign(payload, config.auth.jwtSecret, {
            expiresIn: config.auth.jwtExpiry,
            issuer: config.app.name,
            audience: config.app.url,
        });
        const refreshToken = jwt.sign(payload, config.auth.jwtSecret, {
            expiresIn: config.auth.jwtRefreshExpiry,
            issuer: config.app.name,
            audience: config.app.url,
        });
        return { accessToken, refreshToken };
    }
    verifyToken(token) {
        try {
            const decoded = jwt.verify(token, config.auth.jwtSecret, {
                issuer: config.app.name,
                audience: config.app.url,
            });
            return decoded;
        }
        catch (error) {
            if (error instanceof jwt.TokenExpiredError) {
                throw new AuthenticationError('Token has expired');
            }
            else if (error instanceof jwt.JsonWebTokenError) {
                throw new AuthenticationError('Invalid token');
            }
            else {
                throw new AuthenticationError('Token verification failed');
            }
        }
    }
    async hashPassword(password) {
        return await bcrypt.hash(password, config.auth.bcryptRounds);
    }
    async verifyPassword(password, hashedPassword) {
        return await bcrypt.compare(password, hashedPassword);
    }
    async authenticateUser(email, password) {
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
        if (!user.isActive) {
            logSecurityEvent('FAILED_LOGIN_ATTEMPT', 'high', {
                userId: user.id,
                email,
                reason: 'Account deactivated',
            });
            throw new AuthenticationError('Account is deactivated');
        }
        const isValidPassword = await this.verifyPassword(password, user.password);
        if (!isValidPassword) {
            logSecurityEvent('FAILED_LOGIN_ATTEMPT', 'medium', {
                userId: user.id,
                email,
                reason: 'Invalid password',
            });
            throw new AuthenticationError('Invalid credentials');
        }
        await prisma.user.update({
            where: { id: user.id },
            data: { lastLogin: new Date() },
        });
        logAuthEvent('USER_LOGIN_SUCCESS', user.id, { email });
        return user;
    }
    async registerUser(data) {
        const { email, password, firstName, lastName } = data;
        const existingUser = await prisma.user.findUnique({
            where: { email: email.toLowerCase() },
        });
        if (existingUser) {
            throw new ValidationError('User with this email already exists');
        }
        const hashedPassword = await this.hashPassword(password);
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
    async getUserFromToken(token) {
        const isBlacklisted = await cacheService.exists(`blacklisted_token:${token}`);
        if (isBlacklisted) {
            throw new AuthenticationError('Token has been invalidated');
        }
        const payload = this.verifyToken(token);
        const cacheKey = `user:${payload.userId}`;
        let user = await cacheService.get(cacheKey);
        if (!user) {
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
            await cacheService.set(cacheKey, user, 300);
        }
        return user;
    }
    async refreshTokens(refreshToken) {
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
    async logout(token) {
        const payload = this.verifyToken(token);
        const now = Math.floor(Date.now() / 1000);
        const tokenTTL = payload.exp ? payload.exp - now : 3600;
        if (tokenTTL > 0) {
            await cacheService.set(`blacklisted_token:${token}`, true, tokenTTL);
        }
        logAuthEvent('USER_LOGOUT', payload.userId);
    }
    async changePassword(userId, currentPassword, newPassword) {
        const user = await prisma.user.findUnique({
            where: { id: userId },
        });
        if (!user) {
            throw new AuthenticationError('User not found');
        }
        const isValidPassword = await this.verifyPassword(currentPassword, user.password);
        if (!isValidPassword) {
            throw new AuthenticationError('Current password is incorrect');
        }
        const hashedNewPassword = await this.hashPassword(newPassword);
        await prisma.user.update({
            where: { id: userId },
            data: { password: hashedNewPassword },
        });
        await cacheService.delete(`user:${userId}`);
        logAuthEvent('PASSWORD_CHANGED', userId);
    }
    async initiatePasswordReset(email) {
        const user = await prisma.user.findUnique({
            where: { email: email.toLowerCase() },
        });
        if (!user) {
            return;
        }
        const resetToken = jwt.sign({ userId: user.id, purpose: 'password_reset' }, config.auth.jwtSecret, { expiresIn: '1h' });
        await cacheService.set(`password_reset:${user.id}`, resetToken, 3600);
        logAuthEvent('PASSWORD_RESET_INITIATED', user.id, { email });
    }
    async resetPassword(resetToken, newPassword) {
        try {
            const payload = jwt.verify(resetToken, config.auth.jwtSecret);
            if (payload.purpose !== 'password_reset') {
                throw new AuthenticationError('Invalid reset token');
            }
            const cachedToken = await cacheService.get(`password_reset:${payload.userId}`);
            if (!cachedToken || cachedToken !== resetToken) {
                throw new AuthenticationError('Reset token has expired or been used');
            }
            const hashedPassword = await this.hashPassword(newPassword);
            await prisma.user.update({
                where: { id: payload.userId },
                data: { password: hashedPassword },
            });
            await cacheService.delete(`password_reset:${payload.userId}`);
            await cacheService.delete(`user:${payload.userId}`);
            logAuthEvent('PASSWORD_RESET_COMPLETED', payload.userId);
        }
        catch (error) {
            if (error instanceof jwt.TokenExpiredError) {
                throw new AuthenticationError('Reset token has expired');
            }
            else if (error instanceof jwt.JsonWebTokenError) {
                throw new AuthenticationError('Invalid reset token');
            }
            throw error;
        }
    }
}
export const authService = new AuthService();
export const authenticate = async (req, res, next) => {
    try {
        const authHeader = req.headers.authorization;
        if (!authHeader) {
            throw new AuthenticationError('Authorization header is required');
        }
        const parts = authHeader.split(' ');
        if (parts.length !== 2 || parts[0] !== 'Bearer') {
            throw new AuthenticationError('Invalid authorization header format');
        }
        const token = parts[1];
        const user = await authService.getUserFromToken(token);
        req.user = user;
        req.token = token;
        next();
    }
    catch (error) {
        next(error);
    }
};
export const optionalAuthenticate = async (req, res, next) => {
    try {
        const authHeader = req.headers.authorization;
        if (authHeader) {
            const parts = authHeader.split(' ');
            if (parts.length === 2 && parts[0] === 'Bearer') {
                const token = parts[1];
                const user = await authService.getUserFromToken(token);
                req.user = user;
                req.token = token;
            }
        }
        next();
    }
    catch (error) {
        next();
    }
};
export const authorize = (...roles) => {
    return (req, res, next) => {
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
export const requireOwnership = (resourceUserIdField = 'userId') => {
    return (req, res, next) => {
        if (!req.user) {
            return next(new AuthenticationError('User not authenticated'));
        }
        const resourceUserId = req.params[resourceUserIdField] || req.body[resourceUserIdField];
        if (req.user.role === 'SUPER_ADMIN' || req.user.role === 'ADMIN') {
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
export const userRateLimit = (maxRequests = 100, windowMs = 15 * 60 * 1000) => {
    return async (req, res, next) => {
        if (!req.user) {
            return next();
        }
        const key = `rate_limit:user:${req.user.id}`;
        const windowStart = Math.floor(Date.now() / windowMs) * windowMs;
        const windowKey = `${key}:${windowStart}`;
        try {
            const current = await cacheService.get(windowKey) || 0;
            if (current >= maxRequests) {
                throw new AuthenticationError('Rate limit exceeded');
            }
            await cacheService.increment(windowKey);
            await cacheService.expire(windowKey, Math.ceil(windowMs / 1000));
            next();
        }
        catch (error) {
            next(error);
        }
    };
};
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
//# sourceMappingURL=authMiddleware.js.map