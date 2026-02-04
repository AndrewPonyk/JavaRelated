export interface CacheStrategy {
    name: string;
    ttl: number;
    maxSize?: number;
    warmupEnabled: boolean;
    preloadPatterns?: string[];
    invalidationStrategy: 'immediate' | 'lazy' | 'scheduled';
}
export interface CacheMetrics {
    hits: number;
    misses: number;
    sets: number;
    deletes: number;
    hitRate: number;
    averageResponseTime: number;
    memoryUsage: number;
    keyCount: number;
}
export interface CacheWarmupConfig {
    enabled: boolean;
    strategies: {
        user: boolean;
        dashboard: boolean;
        analytics: boolean;
        search: boolean;
    };
    batchSize: number;
    maxConcurrency: number;
    warmupInterval: number;
}
export declare class CacheOptimizer {
    private static instance;
    private metrics;
    private warmupConfig;
    private strategies;
    private invalidationQueue;
    private constructor();
    static getInstance(): CacheOptimizer;
    private initializeCacheStrategies;
    warmupCache(force?: boolean): Promise<void>;
    optimizeMemoryUsage(): Promise<void>;
    invalidateDistributed(pattern: string, source?: string): Promise<void>;
    performPredictiveCaching(userId: string, patterns: string[]): Promise<void>;
    getMultiLayer<T>(key: string, fetcher: () => Promise<T>, options?: {
        l1TTL?: number;
        l2TTL?: number;
        strategy?: string;
    }): Promise<T>;
    batchGet<T>(keys: string[]): Promise<Map<string, T | null>>;
    getCacheAnalytics(): {
        strategies: Array<{
            name: string;
            metrics: CacheMetrics;
            performance: {
                hitRate: number;
                averageResponseTime: number;
                efficiency: string;
            };
        }>;
        overall: {
            totalHits: number;
            totalMisses: number;
            overallHitRate: number;
            memoryUsage: number;
            recommendations: string[];
        };
    };
    private setupDistributedInvalidation;
    private startBackgroundTasks;
    private warmupUserData;
    private warmupDashboardData;
    private warmupAnalyticsData;
    private executeConcurrently;
    private getCacheMemoryInfo;
    private performIntelligentEviction;
    private cleanupExpiredKeys;
    private optimizeKeyPatterns;
    private analyzeAccessPatterns;
    private generateCachePredictions;
    private preloadData;
    private l1Cache;
    private getFromL1Cache;
    private setL1Cache;
    private recordCacheHit;
    private recordCacheMiss;
    private getDefaultMetrics;
    private generateCacheRecommendations;
    private cleanupMetrics;
}
export declare const cacheOptimizer: CacheOptimizer;
export declare const createOptimizedCacheKey: (prefix: string, ...parts: Array<string | number>) => string;
export declare const getCacheStrategy: (dataType: string) => CacheStrategy | undefined;
//# sourceMappingURL=cacheOptimizer.d.ts.map