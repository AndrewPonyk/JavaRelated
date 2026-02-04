export declare class AppError extends Error {
    readonly statusCode: number;
    readonly isOperational: boolean;
    readonly errorCode?: string;
    readonly details?: any;
    constructor(message: string, statusCode?: number, errorCode?: string, details?: any, isOperational?: boolean);
}
export declare class ValidationError extends AppError {
    constructor(message: string, details?: any);
}
export declare class AuthenticationError extends AppError {
    constructor(message?: string);
}
export declare class AuthorizationError extends AppError {
    constructor(message?: string);
}
export declare class NotFoundError extends AppError {
    constructor(resource?: string);
}
export declare class ConflictError extends AppError {
    constructor(message: string);
}
export declare class RateLimitError extends AppError {
    constructor(message?: string);
}
export declare class DatabaseError extends AppError {
    constructor(message: string, details?: any);
}
export declare class CacheError extends AppError {
    constructor(message: string, details?: any);
}
export declare class ExternalServiceError extends AppError {
    constructor(service: string, message?: string);
}
export interface ErrorResponse {
    success: false;
    error: {
        code: string;
        message: string;
        statusCode: number;
        details?: any;
        stack?: string;
    };
    requestId?: string;
    timestamp: string;
}
export declare const handleError: (error: Error | AppError, requestId?: string) => ErrorResponse;
export declare const errorHandler: (error: Error | AppError, req: any, res: any, next: any) => void;
export declare const asyncHandler: (fn: Function) => (req: any, res: any, next: any) => void;
export declare const createError: {
    validation: (message: string, details?: any) => ValidationError;
    authentication: (message?: string) => AuthenticationError;
    authorization: (message?: string) => AuthorizationError;
    notFound: (resource?: string) => NotFoundError;
    conflict: (message: string) => ConflictError;
    rateLimit: (message?: string) => RateLimitError;
    database: (message: string, details?: any) => DatabaseError;
    cache: (message: string, details?: any) => CacheError;
    externalService: (service: string, message?: string) => ExternalServiceError;
};
export declare const isAppError: (error: any) => error is AppError;
export declare const isOperationalError: (error: any) => boolean;
export declare const notFoundHandler: (req: any, res: any, next: any) => void;
export declare const gracefulShutdown: (server: any, signal: string) => Promise<void>;
//# sourceMappingURL=errors.d.ts.map