import { Request, Response, NextFunction } from 'express';
export interface ResponseOptimizationConfig {
    enableCompression: boolean;
    enableETag: boolean;
    enableConditionalRequests: boolean;
    enableResponseCaching: boolean;
    compressionThreshold: number;
    cacheableStatusCodes: number[];
    excludePatterns: RegExp[];
    compressionLevel: number;
}
export interface OptimizedResponse<T = any> {
    success: boolean;
    data?: T;
    message?: string;
    meta?: {
        timestamp: string;
        requestId?: string;
        duration?: number;
        cached?: boolean;
        compressed?: boolean;
        etag?: string;
    };
}
export declare class ResponseOptimizer {
    private static instance;
    private config;
    private constructor();
    static getInstance(): ResponseOptimizer;
    createOptimizationMiddleware(): (req: Request, res: Response, next: NextFunction) => void;
    createStandardResponse<T>(data: T, message?: string, meta?: any): OptimizedResponse<T>;
    createErrorResponse(error: {
        code: string;
        message: string;
        statusCode: number;
        details?: any;
    }, requestId?: string): OptimizedResponse<never>;
    createCacheMiddleware(options?: {
        ttl?: number;
        varyBy?: string[];
        condition?: (req: Request) => boolean;
        keyGenerator?: (req: Request) => string;
    }): (req: Request, res: Response, next: NextFunction) => Promise<void | Response<any, Record<string, any>>>;
    createConditionalRequestMiddleware(): (req: Request, res: Response, next: NextFunction) => void;
    createCompressionMiddleware(): (req: Request, res: Response, next: NextFunction) => void;
    optimizeJsonSerialization<T>(data: T): string;
    createPaginatedResponse<T>(data: T[], total: number, page: number, limit: number, additionalMeta?: any): OptimizedResponse<{
        items: T[];
        pagination: {
            total: number;
            page: number;
            limit: number;
            pages: number;
            hasNext: boolean;
            hasPrev: boolean;
        };
    }>;
    createStreamResponse(req: Request, res: Response, dataGenerator: AsyncGenerator<any, void, unknown>): void;
    getPerformanceMetrics(): {
        averageResponseTime: number;
        compressionSavings: number;
        cacheHitRate: number;
        totalRequests: number;
    };
    private optimizeJsonResponse;
    private optimizeSendResponse;
    private shouldSkipOptimization;
    private shouldCompress;
    private generateETag;
    private defaultCacheKeyGenerator;
    private serializeArray;
    private serializeObject;
}
export declare const responseOptimizer: ResponseOptimizer;
export declare const optimizeResponses: () => (req: Request, res: Response, next: NextFunction) => void;
export declare const cacheResponses: (options?: any) => (req: Request, res: Response, next: NextFunction) => Promise<void | Response<any, Record<string, any>>>;
export declare const conditionalRequests: () => (req: Request, res: Response, next: NextFunction) => void;
export declare const compressResponses: () => (req: Request, res: Response, next: NextFunction) => void;
export declare const createApiResponse: <T>(data: T, message?: string, meta?: any) => OptimizedResponse<T>;
export declare const createPaginatedApiResponse: <T>(data: T[], total: number, page: number, limit: number, meta?: any) => OptimizedResponse<{
    items: T[];
    pagination: {
        total: number;
        page: number;
        limit: number;
        pages: number;
        hasNext: boolean;
        hasPrev: boolean;
    };
}>;
export declare const createErrorApiResponse: (error: any, requestId?: string) => OptimizedResponse<never>;
declare global {
    namespace Express {
        interface Request {
            startTime?: number;
            id?: string;
        }
    }
}
//# sourceMappingURL=responseOptimizer.d.ts.map