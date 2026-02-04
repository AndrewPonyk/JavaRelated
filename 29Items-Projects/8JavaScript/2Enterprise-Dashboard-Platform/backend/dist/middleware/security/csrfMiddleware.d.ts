import { Response, NextFunction } from 'express';
import type { AuthenticatedRequest } from '@/middleware/auth/authMiddleware.js';
export interface CSRFRequest extends AuthenticatedRequest {
    csrfToken?: string;
}
declare class CSRFTokenService {
    generateToken(): string;
    private getTokenKey;
    storeToken(sessionId: string, token: string): Promise<void>;
    validateAndConsumeToken(sessionId: string, token: string): Promise<boolean>;
    cleanupExpiredTokens(sessionId: string): Promise<void>;
}
export declare const csrfTokenService: CSRFTokenService;
export declare const generateCSRFToken: (req: CSRFRequest, res: Response) => Promise<void>;
export declare const csrfProtection: (req: CSRFRequest, res: Response, next: NextFunction) => Promise<void>;
export declare const conditionalCSRFProtection: (req: CSRFRequest, res: Response, next: NextFunction) => Promise<void>;
export declare const doubleSubmitCookieValidation: (req: CSRFRequest, res: Response, next: NextFunction) => void;
export declare const refreshCSRFToken: (req: CSRFRequest, res: Response) => Promise<void>;
export declare const apiCSRFProtection: (req: CSRFRequest, res: Response, next: NextFunction) => Promise<void>;
declare const _default: {
    csrfProtection: (req: CSRFRequest, res: Response, next: NextFunction) => Promise<void>;
    conditionalCSRFProtection: (req: CSRFRequest, res: Response, next: NextFunction) => Promise<void>;
    doubleSubmitCookieValidation: (req: CSRFRequest, res: Response, next: NextFunction) => void;
    apiCSRFProtection: (req: CSRFRequest, res: Response, next: NextFunction) => Promise<void>;
    generateCSRFToken: (req: CSRFRequest, res: Response) => Promise<void>;
    refreshCSRFToken: (req: CSRFRequest, res: Response) => Promise<void>;
    csrfTokenService: CSRFTokenService;
};
export default _default;
//# sourceMappingURL=csrfMiddleware.d.ts.map