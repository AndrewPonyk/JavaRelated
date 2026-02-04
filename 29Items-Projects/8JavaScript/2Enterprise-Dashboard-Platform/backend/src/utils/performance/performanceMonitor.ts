import { Request, Response, NextFunction } from 'express';
import { performance } from 'perf_hooks';
import { logger } from '@/utils/logger.js';
import { config } from '@/config/environment.js';
import { databaseOptimizer } from './databaseOptimizer.js';
import { cacheOptimizer } from './cacheOptimizer.js';
import { responseOptimizer } from './responseOptimizer.js';
import { backgroundProcessor } from './backgroundProcessor.js';

/**
 * Performance Monitor
 * Comprehensive performance monitoring, alerting, and optimization system
 */

export interface PerformanceMetrics {
  timestamp: Date;
  requests: {
    total: number;
    active: number;
    averageResponseTime: number;
    p95ResponseTime: number;
    p99ResponseTime: number;
    errorRate: number;
    throughput: number; // requests per second
  };
  database: {
    connectionPoolUtilization: number;
    averageQueryTime: number;
    slowQueries: number;
    activeConnections: number;
    healthStatus: 'healthy' | 'degraded' | 'unhealthy';
  };
  cache: {
    hitRate: number;
    memoryUsage: number;
    keyCount: number;
    averageResponseTime: number;
    healthStatus: 'healthy' | 'degraded' | 'unhealthy';
  };
  backgroundJobs: {
    activeJobs: number;
    completedJobs: number;
    failedJobs: number;
    averageProcessingTime: number;
    queueBacklog: number;
  };
  system: {
    memoryUsage: NodeJS.MemoryUsage;
    cpuUsage: NodeJS.CpuUsage;
    uptime: number;
    eventLoopDelay: number;
  };
  alerts: PerformanceAlert[];
}

export interface PerformanceAlert {
  id: string;
  severity: 'low' | 'medium' | 'high' | 'critical';
  type: 'performance' | 'availability' | 'error' | 'resource';
  message: string;
  component: string;
  metric: string;
  value: number;
  threshold: number;
  timestamp: Date;
  acknowledged: boolean;
}

export interface PerformanceThresholds {
  responseTime: {
    warning: number;
    critical: number;
  };
  errorRate: {
    warning: number; // percentage
    critical: number; // percentage
  };
  memoryUsage: {
    warning: number; // percentage
    critical: number; // percentage
  };
  cacheHitRate: {
    warning: number; // percentage (below this is bad)
    critical: number; // percentage
  };
  databaseConnections: {
    warning: number; // percentage of pool
    critical: number; // percentage of pool
  };
  eventLoopDelay: {
    warning: number; // milliseconds
    critical: number; // milliseconds
  };
}

export class PerformanceMonitor {
  private static instance: PerformanceMonitor;
  private metrics: PerformanceMetrics[] = [];
  private alerts: Map<string, PerformanceAlert> = new Map();
  private requestTimings: number[] = [];
  private errorCounts: Map<string, number> = new Map();
  private isMonitoring: boolean = false;

  private thresholds: PerformanceThresholds = {
    responseTime: {
      warning: 1000, // 1 second
      critical: 3000, // 3 seconds
    },
    errorRate: {
      warning: 1, // 1%
      critical: 5, // 5%
    },
    memoryUsage: {
      warning: 80, // 80%
      critical: 90, // 90%
    },
    cacheHitRate: {
      warning: 80, // 80%
      critical: 60, // 60%
    },
    databaseConnections: {
      warning: 80, // 80% of pool
      critical: 90, // 90% of pool
    },
    eventLoopDelay: {
      warning: 10, // 10ms
      critical: 50, // 50ms
    },
  };

  private constructor() {
    this.setupEventListeners();
  }

  public static getInstance(): PerformanceMonitor {
    if (!PerformanceMonitor.instance) {
      PerformanceMonitor.instance = new PerformanceMonitor();
    }
    return PerformanceMonitor.instance;
  }

  /**
   * Start performance monitoring
   */
  public start(): void {
    if (this.isMonitoring) {
      logger.warn('Performance monitoring already started');
      return;
    }

    this.isMonitoring = true;
    logger.info('Starting performance monitoring', {
      environment: config.app.env,
      thresholds: this.thresholds,
    });

    // Start metric collection
    this.startMetricCollection();

    // Start optimization scheduler
    this.startOptimizationScheduler();

    // Start alert checking
    this.startAlertMonitoring();
  }

  /**
   * Stop performance monitoring
   */
  public stop(): void {
    this.isMonitoring = false;
    logger.info('Performance monitoring stopped');
  }

  /**
   * Request timing middleware
   */
  public createTimingMiddleware() {
    return (req: Request, res: Response, next: NextFunction) => {
      const startTime = performance.now();
      const startCpuUsage = process.cpuUsage();

      // Add request ID for tracking
      const requestId = this.generateRequestId();
      (req as any).requestId = requestId;
      (req as any).startTime = startTime;

      // Track active requests
      this.recordRequestStart();

      // Override end method to capture metrics
      const originalEnd = res.end.bind(res);
      (res as any).end = (chunk?: any, encoding?: BufferEncoding, cb?: () => void) => {
        const endTime = performance.now();
        const cpuUsage = process.cpuUsage(startCpuUsage);
        const duration = endTime - startTime;

        // Record metrics
        this.recordRequestEnd(req, res, duration, cpuUsage);

        return originalEnd(chunk, encoding as any, cb);
      };

      next();
    };
  }

  /**
   * Get current performance metrics
   */
  public async getCurrentMetrics(): Promise<PerformanceMetrics> {
    const now = new Date();

    // Collect request metrics
    const requestMetrics = this.calculateRequestMetrics();

    // Collect database metrics
    const databaseHealth = await databaseOptimizer.performHealthCheck();
    const databasePerf = databaseOptimizer.getQueryPerformanceReport();

    // Collect cache metrics
    const cacheAnalytics = cacheOptimizer.getCacheAnalytics();

    // Collect background job metrics
    const jobMetrics = backgroundProcessor.getPerformanceMetrics();

    // Collect system metrics
    const memoryUsage = process.memoryUsage();
    const cpuUsage = process.cpuUsage();
    const eventLoopDelay = await this.measureEventLoopDelay();

    // Get active alerts
    const activeAlerts = Array.from(this.alerts.values())
      .filter(alert => !alert.acknowledged);

    const metrics: PerformanceMetrics = {
      timestamp: now,
      requests: {
        total: this.requestTimings.length,
        active: this.getActiveRequestCount(),
        averageResponseTime: requestMetrics.averageResponseTime,
        p95ResponseTime: requestMetrics.p95ResponseTime,
        p99ResponseTime: requestMetrics.p99ResponseTime,
        errorRate: requestMetrics.errorRate,
        throughput: requestMetrics.throughput,
      },
      database: {
        connectionPoolUtilization: databaseHealth.metrics.poolUtilization,
        averageQueryTime: databasePerf.averageDuration,
        slowQueries: databasePerf.slowQueries.length,
        activeConnections: databaseHealth.metrics.connectionCount,
        healthStatus: databaseHealth.status,
      },
      cache: {
        hitRate: cacheAnalytics.overall.overallHitRate,
        memoryUsage: cacheAnalytics.overall.memoryUsage,
        keyCount: 0, // Would need Redis INFO
        averageResponseTime: 0, // Would need instrumentation
        healthStatus: 'healthy', // Placeholder
      },
      backgroundJobs: {
        activeJobs: jobMetrics.activeJobs,
        completedJobs: jobMetrics.completedJobs,
        failedJobs: jobMetrics.failedJobs,
        averageProcessingTime: jobMetrics.averageProcessingTime,
        queueBacklog: jobMetrics.totalJobs - jobMetrics.completedJobs - jobMetrics.failedJobs,
      },
      system: {
        memoryUsage,
        cpuUsage,
        uptime: process.uptime(),
        eventLoopDelay,
      },
      alerts: activeAlerts,
    };

    return metrics;
  }

  /**
   * Get performance trends over time
   */
  public getPerformanceTrends(hours: number = 24): {
    responseTime: Array<{ timestamp: Date; value: number }>;
    throughput: Array<{ timestamp: Date; value: number }>;
    errorRate: Array<{ timestamp: Date; value: number }>;
    cacheHitRate: Array<{ timestamp: Date; value: number }>;
  } {
    const cutoffTime = new Date(Date.now() - hours * 60 * 60 * 1000);
    const recentMetrics = this.metrics.filter(m => m.timestamp >= cutoffTime);

    return {
      responseTime: recentMetrics.map(m => ({
        timestamp: m.timestamp,
        value: m.requests.averageResponseTime,
      })),
      throughput: recentMetrics.map(m => ({
        timestamp: m.timestamp,
        value: m.requests.throughput,
      })),
      errorRate: recentMetrics.map(m => ({
        timestamp: m.timestamp,
        value: m.requests.errorRate,
      })),
      cacheHitRate: recentMetrics.map(m => ({
        timestamp: m.timestamp,
        value: m.cache.hitRate,
      })),
    };
  }

  /**
   * Get performance recommendations
   */
  public async getPerformanceRecommendations(): Promise<string[]> {
    const metrics = await this.getCurrentMetrics();
    const recommendations: string[] = [];

    // Request performance recommendations
    if (metrics.requests.averageResponseTime > this.thresholds.responseTime.warning) {
      recommendations.push('Average response time is high - consider implementing response caching');
    }

    if (metrics.requests.errorRate > this.thresholds.errorRate.warning) {
      recommendations.push('Error rate is elevated - review error logs and implement better error handling');
    }

    // Database recommendations
    if (metrics.database.connectionPoolUtilization > this.thresholds.databaseConnections.warning) {
      recommendations.push('Database connection pool utilization is high - consider increasing pool size');
    }

    if (metrics.database.slowQueries > 10) {
      recommendations.push('Multiple slow queries detected - review and optimize database queries');
    }

    // Cache recommendations
    if (metrics.cache.hitRate < this.thresholds.cacheHitRate.warning) {
      recommendations.push('Cache hit rate is low - review caching strategy and TTL values');
    }

    // System recommendations
    const memoryUsagePercent = (metrics.system.memoryUsage.heapUsed / metrics.system.memoryUsage.heapTotal) * 100;
    if (memoryUsagePercent > this.thresholds.memoryUsage.warning) {
      recommendations.push('Memory usage is high - consider implementing memory optimization strategies');
    }

    if (metrics.system.eventLoopDelay > this.thresholds.eventLoopDelay.warning) {
      recommendations.push('Event loop delay is high - review CPU-intensive operations');
    }

    // Background job recommendations
    if (metrics.backgroundJobs.queueBacklog > 100) {
      recommendations.push('Background job queue has significant backlog - consider adding more workers');
    }

    return recommendations;
  }

  /**
   * Trigger performance optimization
   */
  public async triggerOptimization(): Promise<void> {
    logger.info('Triggering performance optimization');

    try {
      // Database optimization
      await databaseOptimizer.optimizeConnectionPool();

      // Cache optimization
      await cacheOptimizer.optimizeMemoryUsage();
      await cacheOptimizer.warmupCache(true);

      // Schedule background optimizations
      await backgroundProcessor.addJob('maintenance', 'cache:warm', {});
      await backgroundProcessor.addJob('maintenance', 'database:optimize', {});

      logger.info('Performance optimization completed');

    } catch (error) {
      logger.error('Performance optimization failed', { error });
    }
  }

  /**
   * Acknowledge alert
   */
  public acknowledgeAlert(alertId: string): boolean {
    const alert = this.alerts.get(alertId);
    if (alert) {
      alert.acknowledged = true;
      return true;
    }
    return false;
  }

  // Private methods

  private setupEventListeners(): void {
    // Handle process events
    process.on('warning', (warning) => {
      this.createAlert('low', 'system', 'process_warning', warning.message, 0, 0);
    });

    // Handle uncaught exceptions
    process.on('uncaughtException', (error) => {
      this.createAlert('critical', 'system', 'uncaught_exception', error.message, 0, 0);
    });

    // Handle unhandled rejections
    process.on('unhandledRejection', (reason) => {
      this.createAlert('high', 'system', 'unhandled_rejection', String(reason), 0, 0);
    });
  }

  private startMetricCollection(): void {
    // Collect metrics every minute
    const collectMetrics = async () => {
      if (!this.isMonitoring) return;

      try {
        const metrics = await this.getCurrentMetrics();
        this.metrics.push(metrics);

        // Keep only last 24 hours of metrics
        const cutoffTime = new Date(Date.now() - 24 * 60 * 60 * 1000);
        this.metrics = this.metrics.filter(m => m.timestamp >= cutoffTime);

        // Check for performance issues
        await this.checkPerformanceThresholds(metrics);

      } catch (error) {
        logger.error('Metric collection failed', { error });
      } finally {
        setTimeout(collectMetrics, 60000); // 1 minute
      }
    };

    collectMetrics();
  }

  private startOptimizationScheduler(): void {
    // Run optimization every 30 minutes in production
    if (config.app.env === 'production') {
      const scheduleOptimization = () => {
        if (!this.isMonitoring) return;

        this.triggerOptimization().finally(() => {
          setTimeout(scheduleOptimization, 30 * 60 * 1000); // 30 minutes
        });
      };

      // Start after 10 minutes
      setTimeout(scheduleOptimization, 10 * 60 * 1000);
    }
  }

  private startAlertMonitoring(): void {
    // Check alerts every 30 seconds
    const checkAlerts = async () => {
      if (!this.isMonitoring) return;

      try {
        // Clean up old acknowledged alerts
        const cutoffTime = new Date(Date.now() - 60 * 60 * 1000); // 1 hour
        for (const [id, alert] of this.alerts) {
          if (alert.acknowledged && alert.timestamp < cutoffTime) {
            this.alerts.delete(id);
          }
        }

        // Log active alerts
        const activeAlerts = Array.from(this.alerts.values())
          .filter(alert => !alert.acknowledged);

        if (activeAlerts.length > 0) {
          logger.warn('Active performance alerts', {
            count: activeAlerts.length,
            alerts: activeAlerts.map(a => ({
              severity: a.severity,
              component: a.component,
              message: a.message,
            })),
          });
        }

      } catch (error) {
        logger.error('Alert monitoring failed', { error });
      } finally {
        setTimeout(checkAlerts, 30000); // 30 seconds
      }
    };

    checkAlerts();
  }

  private async checkPerformanceThresholds(metrics: PerformanceMetrics): Promise<void> {
    // Check response time
    if (metrics.requests.averageResponseTime > this.thresholds.responseTime.critical) {
      this.createAlert(
        'critical',
        'api',
        'response_time',
        'Average response time is critically high',
        metrics.requests.averageResponseTime,
        this.thresholds.responseTime.critical
      );
    } else if (metrics.requests.averageResponseTime > this.thresholds.responseTime.warning) {
      this.createAlert(
        'medium',
        'api',
        'response_time',
        'Average response time is high',
        metrics.requests.averageResponseTime,
        this.thresholds.responseTime.warning
      );
    }

    // Check error rate
    if (metrics.requests.errorRate > this.thresholds.errorRate.critical) {
      this.createAlert(
        'critical',
        'api',
        'error_rate',
        'Error rate is critically high',
        metrics.requests.errorRate,
        this.thresholds.errorRate.critical
      );
    }

    // Check memory usage
    const memoryUsagePercent = (metrics.system.memoryUsage.heapUsed / metrics.system.memoryUsage.heapTotal) * 100;
    if (memoryUsagePercent > this.thresholds.memoryUsage.critical) {
      this.createAlert(
        'critical',
        'system',
        'memory_usage',
        'Memory usage is critically high',
        memoryUsagePercent,
        this.thresholds.memoryUsage.critical
      );
    }

    // Check cache hit rate
    if (metrics.cache.hitRate < this.thresholds.cacheHitRate.critical) {
      this.createAlert(
        'high',
        'cache',
        'hit_rate',
        'Cache hit rate is critically low',
        metrics.cache.hitRate,
        this.thresholds.cacheHitRate.critical
      );
    }

    // Check database connections
    if (metrics.database.connectionPoolUtilization > this.thresholds.databaseConnections.critical) {
      this.createAlert(
        'high',
        'database',
        'connection_pool',
        'Database connection pool utilization is critically high',
        metrics.database.connectionPoolUtilization,
        this.thresholds.databaseConnections.critical
      );
    }

    // Check event loop delay
    if (metrics.system.eventLoopDelay > this.thresholds.eventLoopDelay.critical) {
      this.createAlert(
        'high',
        'system',
        'event_loop_delay',
        'Event loop delay is critically high',
        metrics.system.eventLoopDelay,
        this.thresholds.eventLoopDelay.critical
      );
    }
  }

  private createAlert(
    severity: 'low' | 'medium' | 'high' | 'critical',
    component: string,
    metric: string,
    message: string,
    value: number,
    threshold: number
  ): void {
    const alertId = `${component}_${metric}_${Date.now()}`;

    // Don't create duplicate alerts for the same issue
    const existingAlert = Array.from(this.alerts.values())
      .find(a => a.component === component && a.metric === metric && !a.acknowledged);

    if (existingAlert) return;

    const alert: PerformanceAlert = {
      id: alertId,
      severity,
      type: 'performance',
      message,
      component,
      metric,
      value,
      threshold,
      timestamp: new Date(),
      acknowledged: false,
    };

    this.alerts.set(alertId, alert);

    logger.warn('Performance alert created', {
      alertId,
      severity,
      component,
      metric,
      message,
      value,
      threshold,
    });
  }

  private recordRequestStart(): void {
    // Track active requests
  }

  private recordRequestEnd(
    req: Request,
    res: Response,
    duration: number,
    cpuUsage: NodeJS.CpuUsage
  ): void {
    // Record timing
    this.requestTimings.push(duration);

    // Keep only recent timings
    if (this.requestTimings.length > 1000) {
      this.requestTimings = this.requestTimings.slice(-500);
    }

    // Record errors
    if (res.statusCode >= 400) {
      const errorKey = `${res.statusCode}`;
      const currentCount = this.errorCounts.get(errorKey) || 0;
      this.errorCounts.set(errorKey, currentCount + 1);
    }

    // Log slow requests
    if (duration > this.thresholds.responseTime.warning) {
      logger.warn('Slow request detected', {
        method: req.method,
        path: req.path,
        duration,
        statusCode: res.statusCode,
        userAgent: req.headers['user-agent'],
        ip: req.ip,
      });
    }
  }

  private calculateRequestMetrics(): {
    averageResponseTime: number;
    p95ResponseTime: number;
    p99ResponseTime: number;
    errorRate: number;
    throughput: number;
  } {
    const timings = this.requestTimings;
    const errorCount = Array.from(this.errorCounts.values()).reduce((sum, count) => sum + count, 0);

    if (timings.length === 0) {
      return {
        averageResponseTime: 0,
        p95ResponseTime: 0,
        p99ResponseTime: 0,
        errorRate: 0,
        throughput: 0,
      };
    }

    const sortedTimings = [...timings].sort((a, b) => a - b);
    const averageResponseTime = timings.reduce((sum, t) => sum + t, 0) / timings.length;
    const p95ResponseTime = sortedTimings[Math.floor(sortedTimings.length * 0.95)] || 0;
    const p99ResponseTime = sortedTimings[Math.floor(sortedTimings.length * 0.99)] || 0;
    const errorRate = timings.length > 0 ? (errorCount / timings.length) * 100 : 0;
    const throughput = timings.length / 60; // Requests per second (assuming 1-minute window)

    return {
      averageResponseTime,
      p95ResponseTime,
      p99ResponseTime,
      errorRate,
      throughput,
    };
  }

  private getActiveRequestCount(): number {
    // This would be tracked by the timing middleware
    return 0; // Placeholder
  }

  private async measureEventLoopDelay(): Promise<number> {
    return new Promise((resolve) => {
      const start = performance.now();
      setImmediate(() => {
        resolve(performance.now() - start);
      });
    });
  }

  private generateRequestId(): string {
    return `req_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }
}

// Export singleton instance
export const performanceMonitor = PerformanceMonitor.getInstance();

// Middleware exports
export const performanceTimingMiddleware = () => performanceMonitor.createTimingMiddleware();

// Utility functions
export const getCurrentPerformanceMetrics = () => performanceMonitor.getCurrentMetrics();
export const getPerformanceRecommendations = () => performanceMonitor.getPerformanceRecommendations();
export const triggerPerformanceOptimization = () => performanceMonitor.triggerOptimization();