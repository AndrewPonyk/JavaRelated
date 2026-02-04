/**
 * Performance Optimization Suite
 *
 * Comprehensive performance optimization utilities for the Enterprise Dashboard Platform.
 * This module provides database optimization, intelligent caching, response optimization,
 * background job processing, and comprehensive performance monitoring.
 */

// Internal imports for use within this file
import { databaseOptimizer as _databaseOptimizer } from './databaseOptimizer.js';
import { cacheOptimizer as _cacheOptimizer } from './cacheOptimizer.js';
import { cacheResponses as _cacheResponses, createApiResponse as _createApiResponse, createErrorApiResponse as _createErrorApiResponse } from './responseOptimizer.js';
import { backgroundProcessor as _backgroundProcessor, scheduleRecurringJob as _scheduleRecurringJob } from './backgroundProcessor.js';
import { performanceMonitor as _performanceMonitor, getCurrentPerformanceMetrics as _getCurrentPerformanceMetrics, getPerformanceRecommendations as _getPerformanceRecommendations, triggerPerformanceOptimization as _triggerPerformanceOptimization } from './performanceMonitor.js';
import type { PerformanceMetrics as _PerformanceMetrics } from './performanceMonitor.js';

// Database Performance
export {
  DatabaseOptimizer,
  databaseOptimizer,
  optimizeQuery,
  QueryBuilders,
  type QueryMetrics,
  type PerformanceConfig,
} from './databaseOptimizer.js';

// Cache Optimization
export {
  CacheOptimizer,
  cacheOptimizer,
  createOptimizedCacheKey,
  getCacheStrategy,
  type CacheStrategy,
  type CacheMetrics,
  type CacheWarmupConfig,
} from './cacheOptimizer.js';

// Response Optimization
export {
  ResponseOptimizer,
  responseOptimizer,
  optimizeResponses,
  cacheResponses,
  conditionalRequests,
  compressResponses,
  createApiResponse,
  createPaginatedApiResponse,
  createErrorApiResponse,
  type ResponseOptimizationConfig,
  type OptimizedResponse,
} from './responseOptimizer.js';

// Background Processing
export {
  BackgroundProcessor,
  backgroundProcessor,
  scheduleJob,
  scheduleDelayedJob,
  scheduleRecurringJob,
  type Job,
  type JobOptions,
  type JobHandler,
  type QueueStats,
} from './backgroundProcessor.js';

// Performance Monitoring
export {
  PerformanceMonitor,
  performanceMonitor,
  performanceTimingMiddleware,
  getCurrentPerformanceMetrics,
  getPerformanceRecommendations,
  triggerPerformanceOptimization,
  type PerformanceMetrics,
  type PerformanceAlert,
  type PerformanceThresholds,
} from './performanceMonitor.js';

/**
 * Initialize all performance optimization systems
 */
export async function initializePerformanceOptimization(): Promise<void> {
  const { logger } = await import('@/utils/logger.js');
  const { config } = await import('@/config/environment.js');

  try {
    logger.info('Initializing performance optimization suite');

    // Start performance monitoring
    _performanceMonitor.start();

    // Start background processor
    await _backgroundProcessor.start();

    // Initialize cache optimization
    if (config.app.env === 'production') {
      // Warm up cache in production
      await _cacheOptimizer.warmupCache(false);

      // Schedule recurring optimizations
      await _scheduleRecurringJob(
        'maintenance',
        'cache:warm',
        '0 */2 * * *', // Every 2 hours
        {},
        { priority: 1 }
      );

      await _scheduleRecurringJob(
        'maintenance',
        'database:optimize',
        '0 */6 * * *', // Every 6 hours
        {},
        { priority: 2 }
      );
    }

    // Initialize database optimization
    await _databaseOptimizer.optimizeConnectionPool();

    logger.info('Performance optimization suite initialized successfully', {
      environment: config.app.env,
      monitoring: true,
      backgroundProcessing: true,
      cacheOptimization: true,
      databaseOptimization: true,
    });

  } catch (error) {
    logger.error('Failed to initialize performance optimization suite', { error });
    // Don't throw - let server continue without performance optimizations
    logger.warn('Server will continue without performance optimizations');
  }
}

/**
 * Create optimized Express middleware stack
 */
export function createOptimizedMiddleware() {
  // Disabled due to circular dependency issues - server uses built-in compression
  return [];
}

/**
 * Create cached route middleware
 */
export function createCachedRoute(options?: {
  ttl?: number;
  varyBy?: string[];
  condition?: (req: any) => boolean;
}) {
  return _cacheResponses(options);
}

/**
 * Performance health check utility
 */
export async function performPerformanceHealthCheck(): Promise<{
  status: 'healthy' | 'degraded' | 'unhealthy';
  components: {
    database: { status: string; responseTime: number };
    cache: { status: string; hitRate: number };
    backgroundJobs: { status: string; queueBacklog: number };
    api: { status: string; averageResponseTime: number };
  };
  metrics: _PerformanceMetrics;
  recommendations: string[];
}> {
  try {
    const [
      metrics,
      recommendations,
      dbHealth,
      cacheAnalytics,
      jobMetrics
    ] = await Promise.all([
      _getCurrentPerformanceMetrics(),
      _getPerformanceRecommendations(),
      _databaseOptimizer.performHealthCheck(),
      _cacheOptimizer.getCacheAnalytics(),
      _backgroundProcessor.getPerformanceMetrics(),
    ]);

    // Determine overall status
    let overallStatus: 'healthy' | 'degraded' | 'unhealthy' = 'healthy';

    if (
      dbHealth.status === 'unhealthy' ||
      metrics.requests.errorRate > 5 ||
      metrics.system.eventLoopDelay > 50
    ) {
      overallStatus = 'unhealthy';
    } else if (
      dbHealth.status === 'degraded' ||
      metrics.requests.averageResponseTime > 1000 ||
      metrics.cache.hitRate < 60 ||
      metrics.backgroundJobs.queueBacklog > 100
    ) {
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

  } catch (error) {
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
      metrics: {} as _PerformanceMetrics,
      recommendations: ['Performance health check failed - review logs'],
    };
  }
}

/**
 * Graceful shutdown for all performance systems
 */
export async function shutdownPerformanceOptimization(): Promise<void> {
  const { logger } = await import('@/utils/logger.js');

  try {
    logger.info('Shutting down performance optimization suite');

    // Stop performance monitoring
    _performanceMonitor.stop();

    // Stop background processor
    await _backgroundProcessor.stop();

    logger.info('Performance optimization suite shutdown completed');

  } catch (error) {
    logger.error('Error during performance optimization shutdown', { error });
  }
}

/**
 * Express route handler for performance metrics API endpoint
 */
export async function handlePerformanceMetricsRequest(req: any, res: any): Promise<void> {
  try {
    const healthCheck = await performPerformanceHealthCheck();

    res.json(_createApiResponse(
      healthCheck,
      'Performance metrics retrieved successfully',
      {
        timestamp: new Date().toISOString(),
        requestId: req.requestId,
      }
    ));

  } catch (error) {
    const { logger } = await import('@/utils/logger.js');
    logger.error('Performance metrics request failed', { error });

    res.status(500).json(_createErrorApiResponse({
      code: 'PERFORMANCE_METRICS_ERROR',
      message: 'Failed to retrieve performance metrics',
      statusCode: 500,
    }, req.requestId));
  }
}

/**
 * Express route handler for triggering performance optimization
 */
export async function handlePerformanceOptimizationRequest(req: any, res: any): Promise<void> {
  try {
    await _triggerPerformanceOptimization();

    res.json(_createApiResponse(
      { status: 'optimization_triggered' },
      'Performance optimization triggered successfully',
      {
        timestamp: new Date().toISOString(),
        requestId: req.requestId,
      }
    ));

  } catch (error) {
    const { logger } = await import('@/utils/logger.js');
    logger.error('Performance optimization request failed', { error });

    res.status(500).json(_createErrorApiResponse({
      code: 'PERFORMANCE_OPTIMIZATION_ERROR',
      message: 'Failed to trigger performance optimization',
      statusCode: 500,
    }, req.requestId));
  }
}

/**
 * Development utilities for performance debugging
 */
export const PerformanceDevUtils = {
  /**
   * Generate performance report for development
   */
  async generatePerformanceReport(): Promise<object> {
    const [
      healthCheck,
      dbReport,
      cacheAnalytics,
      trends,
    ] = await Promise.all([
      performPerformanceHealthCheck(),
      _databaseOptimizer.getQueryPerformanceReport(),
      _cacheOptimizer.getCacheAnalytics(),
      _performanceMonitor.getPerformanceTrends(1), // Last 1 hour
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
        metrics: await _backgroundProcessor.getPerformanceMetrics(),
        health: healthCheck.components.backgroundJobs,
      },
      recommendations: healthCheck.recommendations,
    };
  },

  /**
   * Log performance statistics to console (development only)
   */
  async logPerformanceStats(): Promise<void> {
    const { config } = await import('@/config/environment.js');

    if (config.app.env !== 'development') return;

    const report = await this.generatePerformanceReport();
    console.table({
      'API Response Time': `${report.summary.status} (${Date.now()}ms)`,
      'Database Status': report.database.health.status,
      'Cache Hit Rate': `${report.cache.analytics.overall.overallHitRate}%`,
      'Background Jobs': report.backgroundJobs.health.status,
    });
  },
};

// Export a simplified interface for common operations
export default {
  initialize: initializePerformanceOptimization,
  createMiddleware: createOptimizedMiddleware,
  createCachedRoute,
  healthCheck: performPerformanceHealthCheck,
  shutdown: shutdownPerformanceOptimization,
  devUtils: PerformanceDevUtils,
};