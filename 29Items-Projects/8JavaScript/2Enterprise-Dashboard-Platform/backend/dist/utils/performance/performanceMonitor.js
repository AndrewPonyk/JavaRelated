import { performance } from 'perf_hooks';
import { logger } from '@/utils/logger.js';
import { config } from '@/config/environment.js';
import { databaseOptimizer } from './databaseOptimizer.js';
import { cacheOptimizer } from './cacheOptimizer.js';
import { backgroundProcessor } from './backgroundProcessor.js';
export class PerformanceMonitor {
    static instance;
    metrics = [];
    alerts = new Map();
    requestTimings = [];
    errorCounts = new Map();
    isMonitoring = false;
    thresholds = {
        responseTime: {
            warning: 1000,
            critical: 3000,
        },
        errorRate: {
            warning: 1,
            critical: 5,
        },
        memoryUsage: {
            warning: 80,
            critical: 90,
        },
        cacheHitRate: {
            warning: 80,
            critical: 60,
        },
        databaseConnections: {
            warning: 80,
            critical: 90,
        },
        eventLoopDelay: {
            warning: 10,
            critical: 50,
        },
    };
    constructor() {
        this.setupEventListeners();
    }
    static getInstance() {
        if (!PerformanceMonitor.instance) {
            PerformanceMonitor.instance = new PerformanceMonitor();
        }
        return PerformanceMonitor.instance;
    }
    start() {
        if (this.isMonitoring) {
            logger.warn('Performance monitoring already started');
            return;
        }
        this.isMonitoring = true;
        logger.info('Starting performance monitoring', {
            environment: config.app.env,
            thresholds: this.thresholds,
        });
        this.startMetricCollection();
        this.startOptimizationScheduler();
        this.startAlertMonitoring();
    }
    stop() {
        this.isMonitoring = false;
        logger.info('Performance monitoring stopped');
    }
    createTimingMiddleware() {
        return (req, res, next) => {
            const startTime = performance.now();
            const startCpuUsage = process.cpuUsage();
            const requestId = this.generateRequestId();
            req.requestId = requestId;
            req.startTime = startTime;
            this.recordRequestStart();
            const originalEnd = res.end;
            res.end = (...args) => {
                const endTime = performance.now();
                const cpuUsage = process.cpuUsage(startCpuUsage);
                const duration = endTime - startTime;
                this.recordRequestEnd(req, res, duration, cpuUsage);
                return originalEnd.apply(res, args);
            };
            next();
        };
    }
    async getCurrentMetrics() {
        const now = new Date();
        const requestMetrics = this.calculateRequestMetrics();
        const databaseHealth = await databaseOptimizer.performHealthCheck();
        const databasePerf = databaseOptimizer.getQueryPerformanceReport();
        const cacheHealth = await cacheOptimizer['healthCheck']();
        const cacheAnalytics = cacheOptimizer.getCacheAnalytics();
        const jobMetrics = backgroundProcessor.getPerformanceMetrics();
        const memoryUsage = process.memoryUsage();
        const cpuUsage = process.cpuUsage();
        const eventLoopDelay = await this.measureEventLoopDelay();
        const activeAlerts = Array.from(this.alerts.values())
            .filter(alert => !alert.acknowledged);
        const metrics = {
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
                keyCount: 0,
                averageResponseTime: 0,
                healthStatus: 'healthy',
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
    getPerformanceTrends(hours = 24) {
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
    async getPerformanceRecommendations() {
        const metrics = await this.getCurrentMetrics();
        const recommendations = [];
        if (metrics.requests.averageResponseTime > this.thresholds.responseTime.warning) {
            recommendations.push('Average response time is high - consider implementing response caching');
        }
        if (metrics.requests.errorRate > this.thresholds.errorRate.warning) {
            recommendations.push('Error rate is elevated - review error logs and implement better error handling');
        }
        if (metrics.database.connectionPoolUtilization > this.thresholds.databaseConnections.warning) {
            recommendations.push('Database connection pool utilization is high - consider increasing pool size');
        }
        if (metrics.database.slowQueries > 10) {
            recommendations.push('Multiple slow queries detected - review and optimize database queries');
        }
        if (metrics.cache.hitRate < this.thresholds.cacheHitRate.warning) {
            recommendations.push('Cache hit rate is low - review caching strategy and TTL values');
        }
        const memoryUsagePercent = (metrics.system.memoryUsage.heapUsed / metrics.system.memoryUsage.heapTotal) * 100;
        if (memoryUsagePercent > this.thresholds.memoryUsage.warning) {
            recommendations.push('Memory usage is high - consider implementing memory optimization strategies');
        }
        if (metrics.system.eventLoopDelay > this.thresholds.eventLoopDelay.warning) {
            recommendations.push('Event loop delay is high - review CPU-intensive operations');
        }
        if (metrics.backgroundJobs.queueBacklog > 100) {
            recommendations.push('Background job queue has significant backlog - consider adding more workers');
        }
        return recommendations;
    }
    async triggerOptimization() {
        logger.info('Triggering performance optimization');
        try {
            await databaseOptimizer.optimizeConnectionPool();
            await cacheOptimizer.optimizeMemoryUsage();
            await cacheOptimizer.warmupCache(true);
            await backgroundProcessor.addJob('maintenance', 'cache:warm', {});
            await backgroundProcessor.addJob('maintenance', 'database:optimize', {});
            logger.info('Performance optimization completed');
        }
        catch (error) {
            logger.error('Performance optimization failed', { error });
        }
    }
    acknowledgeAlert(alertId) {
        const alert = this.alerts.get(alertId);
        if (alert) {
            alert.acknowledged = true;
            return true;
        }
        return false;
    }
    setupEventListeners() {
        process.on('warning', (warning) => {
            this.createAlert('warning', 'system', 'process_warning', warning.message, 0, 0);
        });
        process.on('uncaughtException', (error) => {
            this.createAlert('critical', 'system', 'uncaught_exception', error.message, 0, 0);
        });
        process.on('unhandledRejection', (reason) => {
            this.createAlert('high', 'system', 'unhandled_rejection', String(reason), 0, 0);
        });
    }
    startMetricCollection() {
        const collectMetrics = async () => {
            if (!this.isMonitoring)
                return;
            try {
                const metrics = await this.getCurrentMetrics();
                this.metrics.push(metrics);
                const cutoffTime = new Date(Date.now() - 24 * 60 * 60 * 1000);
                this.metrics = this.metrics.filter(m => m.timestamp >= cutoffTime);
                await this.checkPerformanceThresholds(metrics);
            }
            catch (error) {
                logger.error('Metric collection failed', { error });
            }
            finally {
                setTimeout(collectMetrics, 60000);
            }
        };
        collectMetrics();
    }
    startOptimizationScheduler() {
        if (config.app.env === 'production') {
            const scheduleOptimization = () => {
                if (!this.isMonitoring)
                    return;
                this.triggerOptimization().finally(() => {
                    setTimeout(scheduleOptimization, 30 * 60 * 1000);
                });
            };
            setTimeout(scheduleOptimization, 10 * 60 * 1000);
        }
    }
    startAlertMonitoring() {
        const checkAlerts = async () => {
            if (!this.isMonitoring)
                return;
            try {
                const cutoffTime = new Date(Date.now() - 60 * 60 * 1000);
                for (const [id, alert] of this.alerts) {
                    if (alert.acknowledged && alert.timestamp < cutoffTime) {
                        this.alerts.delete(id);
                    }
                }
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
            }
            catch (error) {
                logger.error('Alert monitoring failed', { error });
            }
            finally {
                setTimeout(checkAlerts, 30000);
            }
        };
        checkAlerts();
    }
    async checkPerformanceThresholds(metrics) {
        if (metrics.requests.averageResponseTime > this.thresholds.responseTime.critical) {
            this.createAlert('critical', 'api', 'response_time', 'Average response time is critically high', metrics.requests.averageResponseTime, this.thresholds.responseTime.critical);
        }
        else if (metrics.requests.averageResponseTime > this.thresholds.responseTime.warning) {
            this.createAlert('medium', 'api', 'response_time', 'Average response time is high', metrics.requests.averageResponseTime, this.thresholds.responseTime.warning);
        }
        if (metrics.requests.errorRate > this.thresholds.errorRate.critical) {
            this.createAlert('critical', 'api', 'error_rate', 'Error rate is critically high', metrics.requests.errorRate, this.thresholds.errorRate.critical);
        }
        const memoryUsagePercent = (metrics.system.memoryUsage.heapUsed / metrics.system.memoryUsage.heapTotal) * 100;
        if (memoryUsagePercent > this.thresholds.memoryUsage.critical) {
            this.createAlert('critical', 'system', 'memory_usage', 'Memory usage is critically high', memoryUsagePercent, this.thresholds.memoryUsage.critical);
        }
        if (metrics.cache.hitRate < this.thresholds.cacheHitRate.critical) {
            this.createAlert('high', 'cache', 'hit_rate', 'Cache hit rate is critically low', metrics.cache.hitRate, this.thresholds.cacheHitRate.critical);
        }
        if (metrics.database.connectionPoolUtilization > this.thresholds.databaseConnections.critical) {
            this.createAlert('high', 'database', 'connection_pool', 'Database connection pool utilization is critically high', metrics.database.connectionPoolUtilization, this.thresholds.databaseConnections.critical);
        }
        if (metrics.system.eventLoopDelay > this.thresholds.eventLoopDelay.critical) {
            this.createAlert('high', 'system', 'event_loop_delay', 'Event loop delay is critically high', metrics.system.eventLoopDelay, this.thresholds.eventLoopDelay.critical);
        }
    }
    createAlert(severity, component, metric, message, value, threshold) {
        const alertId = `${component}_${metric}_${Date.now()}`;
        const existingAlert = Array.from(this.alerts.values())
            .find(a => a.component === component && a.metric === metric && !a.acknowledged);
        if (existingAlert)
            return;
        const alert = {
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
    recordRequestStart() {
    }
    recordRequestEnd(req, res, duration, cpuUsage) {
        this.requestTimings.push(duration);
        if (this.requestTimings.length > 1000) {
            this.requestTimings = this.requestTimings.slice(-500);
        }
        if (res.statusCode >= 400) {
            const errorKey = `${res.statusCode}`;
            const currentCount = this.errorCounts.get(errorKey) || 0;
            this.errorCounts.set(errorKey, currentCount + 1);
        }
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
    calculateRequestMetrics() {
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
        const throughput = timings.length / 60;
        return {
            averageResponseTime,
            p95ResponseTime,
            p99ResponseTime,
            errorRate,
            throughput,
        };
    }
    getActiveRequestCount() {
        return 0;
    }
    async measureEventLoopDelay() {
        return new Promise((resolve) => {
            const start = performance.now();
            setImmediate(() => {
                resolve(performance.now() - start);
            });
        });
    }
    generateRequestId() {
        return `req_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    }
}
export const performanceMonitor = PerformanceMonitor.getInstance();
export const performanceTimingMiddleware = () => performanceMonitor.createTimingMiddleware();
export const getCurrentPerformanceMetrics = () => performanceMonitor.getCurrentMetrics();
export const getPerformanceRecommendations = () => performanceMonitor.getPerformanceRecommendations();
export const triggerPerformanceOptimization = () => performanceMonitor.triggerOptimization();
//# sourceMappingURL=performanceMonitor.js.map