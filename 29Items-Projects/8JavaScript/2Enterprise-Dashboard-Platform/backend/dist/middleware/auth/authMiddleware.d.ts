import { Request, Response, NextFunction } from 'express';
import { z } from 'zod';
import type { User, UserRole } from '@prisma/client';
export interface AuthenticatedRequest extends Request {
    user?: User;
    token?: string;
}
interface JWTPayload {
    userId: string;
    email: string;
    role: UserRole;
    iat?: number;
    exp?: number;
}
interface TokenPair {
    accessToken: string;
    refreshToken: string;
}
export declare class AuthService {
    generateTokens(user: User): TokenPair;
    verifyToken(token: string): JWTPayload;
    hashPassword(password: string): Promise<string>;
    verifyPassword(password: string, hashedPassword: string): Promise<boolean>;
    authenticateUser(email: string, password: string): Promise<User>;
    registerUser(data: {
        email: string;
        password: string;
        firstName?: string;
        lastName?: string;
    }): Promise<User>;
    getUserFromToken(token: string): Promise<User>;
    refreshTokens(refreshToken: string): Promise<TokenPair>;
    logout(token: string): Promise<void>;
    changePassword(userId: string, currentPassword: string, newPassword: string): Promise<void>;
    initiatePasswordReset(email: string): Promise<void>;
    resetPassword(resetToken: string, newPassword: string): Promise<void>;
}
export declare const authService: AuthService;
export declare const authenticate: (req: AuthenticatedRequest, res: Response, next: NextFunction) => Promise<void>;
export declare const optionalAuthenticate: (req: AuthenticatedRequest, res: Response, next: NextFunction) => Promise<void>;
export declare const authorize: (...roles: UserRole[]) => (req: AuthenticatedRequest, res: Response, next: NextFunction) => void;
export declare const requireOwnership: (resourceUserIdField?: string) => (req: AuthenticatedRequest, res: Response, next: NextFunction) => void;
export declare const userRateLimit: (maxRequests?: number, windowMs?: number) => (req: AuthenticatedRequest, res: Response, next: NextFunction) => Promise<void>;
export declare const authSchemas: {
    login: z.ZodObject<{
        email: z.ZodString;
        password: z.ZodString;
    }, "strip", z.ZodTypeAny, {
        email: string;
        password: string;
    }, {
        email: string;
        password: string;
    }>;
    register: z.ZodObject<{
        email: z.ZodString;
        password: z.ZodString;
        firstName: z.ZodOptional<z.ZodString>;
        lastName: z.ZodOptional<z.ZodString>;
    }, "strip", z.ZodTypeAny, {
        email: string;
        password: string;
        firstName?: string | undefined;
        lastName?: string | undefined;
    }, {
        email: string;
        password: string;
        firstName?: string | undefined;
        lastName?: string | undefined;
    }>;
    changePassword: z.ZodObject<{
        currentPassword: z.ZodString;
        newPassword: z.ZodString;
    }, "strip", z.ZodTypeAny, {
        currentPassword: string;
        newPassword: string;
    }, {
        currentPassword: string;
        newPassword: string;
    }>;
    resetPassword: z.ZodObject<{
        email: z.ZodString;
    }, "strip", z.ZodTypeAny, {
        email: string;
    }, {
        email: string;
    }>;
    confirmResetPassword: z.ZodObject<{
        token: z.ZodString;
        newPassword: z.ZodString;
    }, "strip", z.ZodTypeAny, {
        token: string;
        newPassword: string;
    }, {
        token: string;
        newPassword: string;
    }>;
    refreshToken: z.ZodObject<{
        refreshToken: z.ZodString;
    }, "strip", z.ZodTypeAny, {
        refreshToken: string;
    }, {
        refreshToken: string;
    }>;
};
export {};
//# sourceMappingURL=authMiddleware.d.ts.map