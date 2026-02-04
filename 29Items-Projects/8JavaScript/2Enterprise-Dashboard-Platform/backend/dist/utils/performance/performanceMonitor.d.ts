import { Request, Response, NextFunction } from 'express';
export interface PerformanceMetrics {
    timestamp: Date;
    requests: {
        total: number;
        active: number;
        averageResponseTime: number;
        p95ResponseTime: number;
        p99ResponseTime: number;
        errorRate: number;
        throughput: number;
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
        warning: number;
        critical: number;
    };
    memoryUsage: {
        warning: number;
        critical: number;
    };
    cacheHitRate: {
        warning: number;
        critical: number;
    };
    databaseConnections: {
        warning: number;
        critical: number;
    };
    eventLoopDelay: {
        warning: number;
        critical: number;
    };
}
export declare class PerformanceMonitor {
    private static instance;
    private metrics;
    private alerts;
    private requestTimings;
    private errorCounts;
    private isMonitoring;
    private thresholds;
    private constructor();
    static getInstance(): PerformanceMonitor;
    start(): void;
    stop(): void;
    createTimingMiddleware(): (req: Request, res: Response, next: NextFunction) => void;
    getCurrentMetrics(): Promise<PerformanceMetrics>;
    getPerformanceTrends(hours?: number): {
        responseTime: Array<{
            timestamp: Date;
            value: number;
        }>;
        throughput: Array<{
            timestamp: Date;
            value: number;
        }>;
        errorRate: Array<{
            timestamp: Date;
            value: number;
        }>;
        cacheHitRate: Array<{
            timestamp: Date;
            value: number;
        }>;
    };
    getPerformanceRecommendations(): Promise<string[]>;
    triggerOptimization(): Promise<void>;
    acknowledgeAlert(alertId: string): boolean;
    private setupEventListeners;
    private startMetricCollection;
    private startOptimizationScheduler;
    private startAlertMonitoring;
    private checkPerformanceThresholds;
    private createAlert;
    private recordRequestStart;
    private recordRequestEnd;
    private calculateRequestMetrics;
    private getActiveRequestCount;
    private measureEventLoopDelay;
    private generateRequestId;
}
export declare const performanceMonitor: PerformanceMonitor;
export declare const performanceTimingMiddleware: () => (req: Request, res: Response, next: NextFunction) => void;
export declare const getCurrentPerformanceMetrics: () => Promise<PerformanceMetrics>;
export declare const getPerformanceRecommendations: () => Promise<string[]>;
export declare const triggerPerformanceOptimization: () => Promise<void>;
//# sourceMappingURL=performanceMonitor.d.ts.map