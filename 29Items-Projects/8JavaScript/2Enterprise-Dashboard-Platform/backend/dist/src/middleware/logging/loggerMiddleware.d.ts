import { Request, Response, NextFunction } from 'express';
import { PerformanceLogger } from '@/utils/logger.js';
interface LoggedRequest extends Request {
    requestId: string;
    startTime: number;
    performanceLogger: PerformanceLogger;
}
export declare const requestLogger: (req: LoggedRequest, res: Response, next: NextFunction) => void;
export declare const errorLogger: (error: Error, req: LoggedRequest, res: Response, next: NextFunction) => void;
export declare const securityLogger: (eventType: string) => (req: LoggedRequest, res: Response, next: NextFunction) => void;
export declare const dbQueryLogger: (operation: string) => (req: LoggedRequest, res: Response, next: NextFunction) => void;
export declare const endpointLogger: (endpoint: string) => (req: LoggedRequest, res: Response, next: NextFunction) => void;
export declare const businessLogger: (action: string) => (req: LoggedRequest, res: Response, next: NextFunction) => void;
export declare const rateLimitLogger: (req: LoggedRequest, res: Response, next: NextFunction) => void;
export declare const auditLogger: (operation: string, resourceType?: string) => (req: LoggedRequest, res: Response, next: NextFunction) => void;
export declare const createStructuredLogger: (context: Record<string, any>) => {
    info: (message: string, meta?: Record<string, any>) => void;
    warn: (message: string, meta?: Record<string, any>) => void;
    error: (message: string, meta?: Record<string, any>) => void;
    debug: (message: string, meta?: Record<string, any>) => void;
};
declare const _default: {
    requestLogger: (req: LoggedRequest, res: Response, next: NextFunction) => void;
    errorLogger: (error: Error, req: LoggedRequest, res: Response, next: NextFunction) => void;
    securityLogger: (eventType: string) => (req: LoggedRequest, res: Response, next: NextFunction) => void;
    dbQueryLogger: (operation: string) => (req: LoggedRequest, res: Response, next: NextFunction) => void;
    endpointLogger: (endpoint: string) => (req: LoggedRequest, res: Response, next: NextFunction) => void;
    businessLogger: (action: string) => (req: LoggedRequest, res: Response, next: NextFunction) => void;
    rateLimitLogger: (req: LoggedRequest, res: Response, next: NextFunction) => void;
    auditLogger: (operation: string, resourceType?: string) => (req: LoggedRequest, res: Response, next: NextFunction) => void;
    createStructuredLogger: (context: Record<string, any>) => {
        info: (message: string, meta?: Record<string, any>) => void;
        warn: (message: string, meta?: Record<string, any>) => void;
        error: (message: string, meta?: Record<string, any>) => void;
        debug: (message: string, meta?: Record<string, any>) => void;
    };
};
export default _default;
//# sourceMappingURL=loggerMiddleware.d.ts.map