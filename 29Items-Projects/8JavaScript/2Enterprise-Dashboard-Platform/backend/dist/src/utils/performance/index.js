export { DatabaseOptimizer, databaseOptimizer, optimizeQuery, QueryBuilders, } from './databaseOptimizer.js';
export { CacheOptimizer, cacheOptimizer, createOptimizedCacheKey, getCacheStrategy, } from './cacheOptimizer.js';
export { ResponseOptimizer, responseOptimizer, optimizeResponses, cacheResponses, conditionalRequests, compressResponses, createApiResponse, createPaginatedApiResponse, createErrorApiResponse, } from './responseOptimizer.js';
export { BackgroundProcessor, backgroundProcessor, scheduleJob, scheduleDelayedJob, scheduleRecurringJob, } from './backgroundProcessor.js';
export { PerformanceMonitor, performanceMonitor, performanceTimingMiddleware, getCurrentPerformanceMetrics, getPerformanceRecommendations, triggerPerformanceOptimization, } from './performanceMonitor.js';
export async function initializePerformanceOptimization() {
    const { logger } = await import('@/utils/logger.js');
    const { config } = await import('@/config/environment.js');
    try {
        logger.info('Initializing performance optimization suite');
        performanceMonitor.start();
        await backgroundProcessor.start();
        if (config.app.env === 'production') {
            await cacheOptimizer.warmupCache(false);
            await scheduleRecurringJob('maintenance', 'cache:warm', '0 */2 * * *', {}, { priority: 1 });
            await scheduleRecurringJob('maintenance', 'database:optimize', '0 */6 * * *', {}, { priority: 2 });
        }
        await databaseOptimizer.optimizeConnectionPool();
        logger.info('Performance optimization suite initialized successfully', {
            environment: config.app.env,
            monitoring: true,
            backgroundProcessing: true,
            cacheOptimization: true,
            databaseOptimization: true,
        });
    }
    catch (error) {
        logger.error('Failed to initialize performance optimization suite', { error });
        throw error;
    }
}
export function createOptimizedMiddleware() {
    return [
        performanceTimingMiddleware(),
        optimizeResponses(),
        conditionalRequests(),
        compressResponses(),
    ];
}
export function createCachedRoute(options) {
    return cacheResponses(options);
}
export async function performPerformanceHealthCheck() {
    try {
        const [metrics, recommendations, dbHealth, cacheAnalytics, jobMetrics] = await Promise.all([
            getCurrentPerformanceMetrics(),
            getPerformanceRecommendations(),
            databaseOptimizer.performHealthCheck(),
            cacheOptimizer.getCacheAnalytics(),
            backgroundProcessor.getPerformanceMetrics(),
        ]);
        let overallStatus = 'healthy';
        if (dbHealth.status === 'unhealthy' ||
            metrics.requests.errorRate > 5 ||
            metrics.system.eventLoopDelay > 50) {
            overallStatus = 'unhealthy';
        }
        else if (dbHealth.status === 'degraded' ||
            metrics.requests.averageResponseTime > 1000 ||
            metrics.cache.hitRate < 60 ||
            metrics.backgroundJobs.queueBacklog > 100) {
            overallStatus = 'degraded';
        }
        return {
            status: overallStatus,
            components: {
                database: {
                    status: dbHealth.status,
                    responseTime: dbHealth.metrics.queryTime,
                },
                cache: {
                    status: metrics.cache.healthStatus,
                    hitRate: metrics.cache.hitRate,
                },
                backgroundJobs: {
                    status: jobMetrics.failedJobs > 10 ? 'unhealthy' : 'healthy',
                    queueBacklog: metrics.backgroundJobs.queueBacklog,
                },
                api: {
                    status: metrics.requests.averageResponseTime > 3000 ? 'unhealthy' : 'healthy',
                    averageResponseTime: metrics.requests.averageResponseTime,
                },
            },
            metrics,
            recommendations,
        };
    }
    catch (error) {
        const { logger } = await import('@/utils/logger.js');
        logger.error('Performance health check failed', { error });
        return {
            status: 'unhealthy',
            components: {
                database: { status: 'unknown', responseTime: 0 },
                cache: { status: 'unknown', hitRate: 0 },
                backgroundJobs: { status: 'unknown', queueBacklog: 0 },
                api: { status: 'unknown', averageResponseTime: 0 },
            },
            metrics: {},
            recommendations: ['Performance health check failed - review logs'],
        };
    }
}
export async function shutdownPerformanceOptimization() {
    const { logger } = await import('@/utils/logger.js');
    try {
        logger.info('Shutting down performance optimization suite');
        performanceMonitor.stop();
        await backgroundProcessor.stop();
        logger.info('Performance optimization suite shutdown completed');
    }
    catch (error) {
        logger.error('Error during performance optimization shutdown', { error });
    }
}
export async function handlePerformanceMetricsRequest(req, res) {
    try {
        const healthCheck = await performPerformanceHealthCheck();
        res.json(createApiResponse(healthCheck, 'Performance metrics retrieved successfully', {
            timestamp: new Date().toISOString(),
            requestId: req.requestId,
        }));
    }
    catch (error) {
        const { logger } = await import('@/utils/logger.js');
        logger.error('Performance metrics request failed', { error });
        res.status(500).json(createErrorApiResponse({
            code: 'PERFORMANCE_METRICS_ERROR',
            message: 'Failed to retrieve performance metrics',
            statusCode: 500,
        }, req.requestId));
    }
}
export async function handlePerformanceOptimizationRequest(req, res) {
    try {
        await triggerPerformanceOptimization();
        res.json(createApiResponse({ status: 'optimization_triggered' }, 'Performance optimization triggered successfully', {
            timestamp: new Date().toISOString(),
            requestId: req.requestId,
        }));
    }
    catch (error) {
        const { logger } = await import('@/utils/logger.js');
        logger.error('Performance optimization request failed', { error });
        res.status(500).json(createErrorApiResponse({
            code: 'PERFORMANCE_OPTIMIZATION_ERROR',
            message: 'Failed to trigger performance optimization',
            statusCode: 500,
        }, req.requestId));
    }
}
export const PerformanceDevUtils = {
    async generatePerformanceReport() {
        const [healthCheck, dbReport, cacheAnalytics, trends,] = await Promise.all([
            performPerformanceHealthCheck(),
            databaseOptimizer.getQueryPerformanceReport(),
            cacheOptimizer.getCacheAnalytics(),
            performanceMonitor.getPerformanceTrends(1),
        ]);
        return {
            summary: {
                status: healthCheck.status,
                timestamp: new Date().toISOString(),
            },
            database: {
                queries: dbReport,
                health: healthCheck.components.database,
            },
            cache: {
                analytics: cacheAnalytics,
                health: healthCheck.components.cache,
            },
            api: {
                trends,
                health: healthCheck.components.api,
            },
            backgroundJobs: {
                metrics: await backgroundProcessor.getPerformanceMetrics(),
                health: healthCheck.components.backgroundJobs,
            },
            recommendations: healthCheck.recommendations,
        };
    },
    async logPerformanceStats() {
        const { config } = await import('@/config/environment.js');
        if (config.app.env !== 'development')
            return;
        const report = await this.generatePerformanceReport();
        console.table({
            'API Response Time': `${report.summary.status} (${Date.now()}ms)`,
            'Database Status': report.database.health.status,
            'Cache Hit Rate': `${report.cache.analytics.overall.overallHitRate}%`,
            'Background Jobs': report.backgroundJobs.health.status,
        });
    },
};
export default {
    initialize: initializePerformanceOptimization,
    createMiddleware: createOptimizedMiddleware,
    createCachedRoute,
    healthCheck: performPerformanceHealthCheck,
    shutdown: shutdownPerformanceOptimization,
    devUtils: PerformanceDevUtils,
};
//# sourceMappingURL=index.js.map