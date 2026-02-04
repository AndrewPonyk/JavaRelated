import { Request, Response } from 'express';
import { AuthenticatedRequest } from '@/middleware/auth/authMiddleware.js';
export declare class AuthController {
    register: (req: Request<import("express-serve-static-core").ParamsDictionary, any, any, import("qs").ParsedQs, Record<string, any>>, res: Response, next: import("express").NextFunction) => void;
    login: (req: Request<import("express-serve-static-core").ParamsDictionary, any, any, import("qs").ParsedQs, Record<string, any>>, res: Response, next: import("express").NextFunction) => void;
    refresh: (req: Request<import("express-serve-static-core").ParamsDictionary, any, any, import("qs").ParsedQs, Record<string, any>>, res: Response, next: import("express").NextFunction) => void;
    logout: (req: AuthenticatedRequest, res: Response, next: import("express").NextFunction) => void;
    changePassword: (req: AuthenticatedRequest, res: Response, next: import("express").NextFunction) => void;
    forgotPassword: (req: Request<import("express-serve-static-core").ParamsDictionary, any, any, import("qs").ParsedQs, Record<string, any>>, res: Response, next: import("express").NextFunction) => void;
    resetPassword: (req: Request<import("express-serve-static-core").ParamsDictionary, any, any, import("qs").ParsedQs, Record<string, any>>, res: Response, next: import("express").NextFunction) => void;
    verifyToken: (req: AuthenticatedRequest, res: Response, next: import("express").NextFunction) => void;
    getCurrentUser: (req: AuthenticatedRequest, res: Response, next: import("express").NextFunction) => void;
    private checkLoginRateLimit;
    private trackFailedLogin;
}
export declare const authController: AuthController;
export declare const register: (req: Request<import("express-serve-static-core").ParamsDictionary, any, any, import("qs").ParsedQs, Record<string, any>>, res: Response, next: import("express").NextFunction) => void, login: (req: Request<import("express-serve-static-core").ParamsDictionary, any, any, import("qs").ParsedQs, Record<string, any>>, res: Response, next: import("express").NextFunction) => void, refresh: (req: Request<import("express-serve-static-core").ParamsDictionary, any, any, import("qs").ParsedQs, Record<string, any>>, res: Response, next: import("express").NextFunction) => void, logout: (req: AuthenticatedRequest, res: Response, next: import("express").NextFunction) => void, changePassword: (req: AuthenticatedRequest, res: Response, next: import("express").NextFunction) => void, forgotPassword: (req: Request<import("express-serve-static-core").ParamsDictionary, any, any, import("qs").ParsedQs, Record<string, any>>, res: Response, next: import("express").NextFunction) => void, resetPassword: (req: Request<import("express-serve-static-core").ParamsDictionary, any, any, import("qs").ParsedQs, Record<string, any>>, res: Response, next: import("express").NextFunction) => void, verifyToken: (req: AuthenticatedRequest, res: Response, next: import("express").NextFunction) => void, getCurrentUser: (req: AuthenticatedRequest, res: Response, next: import("express").NextFunction) => void;
//# sourceMappingURL=authController.d.ts.map