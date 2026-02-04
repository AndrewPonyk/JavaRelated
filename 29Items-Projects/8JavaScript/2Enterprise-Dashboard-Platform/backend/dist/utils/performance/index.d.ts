export { DatabaseOptimizer, databaseOptimizer, optimizeQuery, QueryBuilders, type QueryMetrics, type PerformanceConfig, } from './databaseOptimizer.js';
export { CacheOptimizer, cacheOptimizer, createOptimizedCacheKey, getCacheStrategy, type CacheStrategy, type CacheMetrics, type CacheWarmupConfig, } from './cacheOptimizer.js';
export { ResponseOptimizer, responseOptimizer, optimizeResponses, cacheResponses, conditionalRequests, compressResponses, createApiResponse, createPaginatedApiResponse, createErrorApiResponse, type ResponseOptimizationConfig, type OptimizedResponse, } from './responseOptimizer.js';
export { BackgroundProcessor, backgroundProcessor, scheduleJob, scheduleDelayedJob, scheduleRecurringJob, type Job, type JobOptions, type JobHandler, type QueueStats, } from './backgroundProcessor.js';
export { PerformanceMonitor, performanceMonitor, performanceTimingMiddleware, getCurrentPerformanceMetrics, getPerformanceRecommendations, triggerPerformanceOptimization, type PerformanceMetrics, type PerformanceAlert, type PerformanceThresholds, } from './performanceMonitor.js';
export declare function initializePerformanceOptimization(): Promise<void>;
export declare function createOptimizedMiddleware(): any[];
export declare function createCachedRoute(options?: {
    ttl?: number;
    varyBy?: string[];
    condition?: (req: any) => boolean;
}): any;
export declare function performPerformanceHealthCheck(): Promise<{
    status: 'healthy' | 'degraded' | 'unhealthy';
    components: {
        database: {
            status: string;
            responseTime: number;
        };
        cache: {
            status: string;
            hitRate: number;
        };
        backgroundJobs: {
            status: string;
            queueBacklog: number;
        };
        api: {
            status: string;
            averageResponseTime: number;
        };
    };
    metrics: PerformanceMetrics;
    recommendations: string[];
}>;
export declare function shutdownPerformanceOptimization(): Promise<void>;
export declare function handlePerformanceMetricsRequest(req: any, res: any): Promise<void>;
export declare function handlePerformanceOptimizationRequest(req: any, res: any): Promise<void>;
export declare const PerformanceDevUtils: {
    generatePerformanceReport(): Promise<object>;
    logPerformanceStats(): Promise<void>;
};
declare const _default: {
    initialize: typeof initializePerformanceOptimization;
    createMiddleware: typeof createOptimizedMiddleware;
    createCachedRoute: typeof createCachedRoute;
    healthCheck: typeof performPerformanceHealthCheck;
    shutdown: typeof shutdownPerformanceOptimization;
    devUtils: {
        generatePerformanceReport(): Promise<object>;
        logPerformanceStats(): Promise<void>;
    };
};
export default _default;
//# sourceMappingURL=index.d.ts.map