import winston from 'winston';
export declare const logger: winston.Logger;
export declare const createRequestLogger: () => (req: any, res: any, next: any) => void;
export declare const logError: (error: Error, context?: Record<string, any>) => void;
export declare const logDatabaseOperation: (operation: string, details: Record<string, any>) => void;
export declare const logCacheOperation: (operation: string, key: string, hit?: boolean) => void;
export declare const logAuthEvent: (event: string, userId?: string, details?: Record<string, any>) => void;
export declare const logApiPerformance: (endpoint: string, method: string, duration: number, statusCode: number) => void;
export declare const logBusinessEvent: (event: string, details: Record<string, any>) => void;
export declare const logSecurityEvent: (event: string, severity: "low" | "medium" | "high" | "critical", details: Record<string, any>) => void;
export declare const createLogger: (module: string) => {
    info: (message: string, meta?: Record<string, any>) => void;
    warn: (message: string, meta?: Record<string, any>) => void;
    error: (message: string, meta?: Record<string, any>) => void;
    debug: (message: string, meta?: Record<string, any>) => void;
};
export declare class PerformanceLogger {
    private startTime;
    private context;
    constructor(context?: Record<string, any>);
    mark(checkpoint: string): void;
    end(operation: string): void;
}
export declare const cleanupLogs: (daysToKeep?: number) => void;
export default logger;
//# sourceMappingURL=logger.d.ts.map