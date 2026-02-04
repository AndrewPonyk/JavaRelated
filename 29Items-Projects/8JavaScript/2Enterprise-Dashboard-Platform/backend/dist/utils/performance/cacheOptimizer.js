import { redis, redisPublisher, redisSubscriber } from '@/config/redis.js';
import { extendedCacheService } from '@/services/cache/cacheService.js';
import { logger } from '@/utils/logger.js';
import { config } from '@/config/environment.js';
export class CacheOptimizer {
    static instance;
    metrics = new Map();
    warmupConfig;
    strategies = new Map();
    invalidationQueue = [];
    constructor() {
        this.warmupConfig = {
            enabled: config.app.env === 'production',
            strategies: {
                user: true,
                dashboard: true,
                analytics: true,
                search: false,
            },
            batchSize: 50,
            maxConcurrency: 5,
            warmupInterval: 60,
        };
        this.initializeCacheStrategies();
        this.setupDistributedInvalidation();
        this.startBackgroundTasks();
    }
    static getInstance() {
        if (!CacheOptimizer.instance) {
            CacheOptimizer.instance = new CacheOptimizer();
        }
        return CacheOptimizer.instance;
    }
    initializeCacheStrategies() {
        this.strategies.set('user-profile', {
            name: 'user-profile',
            ttl: 1800,
            maxSize: 10000,
            warmupEnabled: true,
            preloadPatterns: ['user:*:profile'],
            invalidationStrategy: 'immediate',
        });
        this.strategies.set('dashboard-metadata', {
            name: 'dashboard-metadata',
            ttl: 900,
            maxSize: 5000,
            warmupEnabled: true,
            preloadPatterns: ['dashboard:*:metadata'],
            invalidationStrategy: 'immediate',
        });
        this.strategies.set('dashboard-data', {
            name: 'dashboard-data',
            ttl: 300,
            maxSize: 2000,
            warmupEnabled: false,
            invalidationStrategy: 'lazy',
        });
        this.strategies.set('analytics-aggregated', {
            name: 'analytics-aggregated',
            ttl: 3600,
            maxSize: 1000,
            warmupEnabled: true,
            preloadPatterns: ['analytics:*:daily', 'analytics:*:weekly'],
            invalidationStrategy: 'scheduled',
        });
        this.strategies.set('search-results', {
            name: 'search-results',
            ttl: 600,
            maxSize: 500,
            warmupEnabled: false,
            invalidationStrategy: 'lazy',
        });
    }
    async warmupCache(force = false) {
        if (!this.warmupConfig.enabled && !force) {
            logger.debug('Cache warmup disabled');
            return;
        }
        const startTime = Date.now();
        logger.info('Starting cache warmup process');
        try {
            const warmupTasks = [];
            if (this.warmupConfig.strategies.user) {
                warmupTasks.push(this.warmupUserData());
            }
            if (this.warmupConfig.strategies.dashboard) {
                warmupTasks.push(this.warmupDashboardData());
            }
            if (this.warmupConfig.strategies.analytics) {
                warmupTasks.push(this.warmupAnalyticsData());
            }
            await this.executeConcurrently(warmupTasks, this.warmupConfig.maxConcurrency);
            const duration = Date.now() - startTime;
            logger.info('Cache warmup completed', {
                duration,
                tasksCompleted: warmupTasks.length
            });
            if (!force) {
                setTimeout(() => {
                    this.warmupCache(false);
                }, this.warmupConfig.warmupInterval * 60 * 1000);
            }
        }
        catch (error) {
            logger.error('Cache warmup failed', { error });
        }
    }
    async optimizeMemoryUsage() {
        try {
            const memoryInfo = await this.getCacheMemoryInfo();
            if (memoryInfo.usagePercentage > 80) {
                logger.warn('High cache memory usage detected', memoryInfo);
                await this.performIntelligentEviction();
            }
            await this.cleanupExpiredKeys();
            await this.optimizeKeyPatterns();
        }
        catch (error) {
            logger.error('Memory optimization failed', { error });
        }
    }
    async invalidateDistributed(pattern, source = 'local') {
        try {
            this.invalidationQueue.push({
                pattern,
                timestamp: new Date(),
                source,
            });
            await redisPublisher.publish('cache:invalidate', JSON.stringify({
                pattern,
                timestamp: new Date().toISOString(),
                source,
            }));
            const deletedKeys = await extendedCacheService.invalidatePattern(pattern);
            logger.info('Distributed cache invalidation completed', {
                pattern,
                deletedKeys,
                source
            });
        }
        catch (error) {
            logger.error('Distributed cache invalidation failed', { error, pattern });
        }
    }
    async performPredictiveCaching(userId, patterns) {
        try {
            const accessPatterns = await this.analyzeAccessPatterns(userId);
            const predictions = this.generateCachePredictions(accessPatterns, patterns);
            for (const prediction of predictions) {
                await this.preloadData(prediction);
            }
            logger.debug('Predictive caching completed', {
                userId,
                predictions: predictions.length
            });
        }
        catch (error) {
            logger.error('Predictive caching failed', { error, userId });
        }
    }
    async getMultiLayer(key, fetcher, options = {}) {
        const { l1TTL = 60, l2TTL = 300, strategy = 'default' } = options;
        try {
            const l1Value = this.getFromL1Cache(key);
            if (l1Value !== null) {
                this.recordCacheHit(key, 'L1');
                return l1Value;
            }
            const l2Value = await extendedCacheService.get(key);
            if (l2Value !== null) {
                this.setL1Cache(key, l2Value, l1TTL);
                this.recordCacheHit(key, 'L2');
                return l2Value;
            }
            const value = await fetcher();
            this.setL1Cache(key, value, l1TTL);
            await extendedCacheService.set(key, value, l2TTL);
            this.recordCacheMiss(key);
            return value;
        }
        catch (error) {
            logger.error('Multi-layer cache get failed', { error, key });
            throw error;
        }
    }
    async batchGet(keys) {
        const results = new Map();
        try {
            const values = await redis.mget(...keys);
            keys.forEach((key, index) => {
                const value = values[index];
                if (value) {
                    try {
                        results.set(key, JSON.parse(value));
                    }
                    catch {
                        results.set(key, null);
                    }
                }
                else {
                    results.set(key, null);
                }
            });
            return results;
        }
        catch (error) {
            logger.error('Batch get failed', { error, keyCount: keys.length });
            for (const key of keys) {
                try {
                    const value = await extendedCacheService.get(key);
                    results.set(key, value);
                }
                catch {
                    results.set(key, null);
                }
            }
            return results;
        }
    }
    getCacheAnalytics() {
        const strategyAnalytics = Array.from(this.strategies.keys()).map(strategyName => {
            const metrics = this.metrics.get(strategyName) || this.getDefaultMetrics();
            const hitRate = metrics.hits + metrics.misses > 0
                ? (metrics.hits / (metrics.hits + metrics.misses)) * 100
                : 0;
            return {
                name: strategyName,
                metrics,
                performance: {
                    hitRate,
                    averageResponseTime: metrics.averageResponseTime,
                    efficiency: hitRate > 80 ? 'excellent' : hitRate > 60 ? 'good' : 'needs improvement'
                }
            };
        });
        const totalHits = Array.from(this.metrics.values()).reduce((sum, m) => sum + m.hits, 0);
        const totalMisses = Array.from(this.metrics.values()).reduce((sum, m) => sum + m.misses, 0);
        const overallHitRate = totalHits + totalMisses > 0
            ? (totalHits / (totalHits + totalMisses)) * 100
            : 0;
        const recommendations = this.generateCacheRecommendations(strategyAnalytics);
        return {
            strategies: strategyAnalytics,
            overall: {
                totalHits,
                totalMisses,
                overallHitRate,
                memoryUsage: 0,
                recommendations
            }
        };
    }
    setupDistributedInvalidation() {
        redisSubscriber.subscribe('cache:invalidate');
        redisSubscriber.on('message', async (channel, message) => {
            if (channel === 'cache:invalidate') {
                try {
                    const event = JSON.parse(message);
                    if (event.source === 'local')
                        return;
                    await extendedCacheService.invalidatePattern(event.pattern);
                    logger.debug('Processed distributed cache invalidation', {
                        pattern: event.pattern,
                        source: event.source
                    });
                }
                catch (error) {
                    logger.error('Failed to process cache invalidation event', { error, message });
                }
            }
        });
    }
    startBackgroundTasks() {
        setInterval(() => {
            this.optimizeMemoryUsage();
        }, 30 * 60 * 1000);
        if (this.warmupConfig.enabled) {
            setTimeout(() => {
                this.warmupCache(false);
            }, 5 * 60 * 1000);
        }
        setInterval(() => {
            this.cleanupMetrics();
        }, 60 * 60 * 1000);
    }
    async warmupUserData() {
        logger.debug('Warming up user data cache');
    }
    async warmupDashboardData() {
        logger.debug('Warming up dashboard data cache');
    }
    async warmupAnalyticsData() {
        logger.debug('Warming up analytics data cache');
    }
    async executeConcurrently(tasks, maxConcurrency) {
        const results = [];
        for (let i = 0; i < tasks.length; i += maxConcurrency) {
            const batch = tasks.slice(i, i + maxConcurrency);
            const batchResults = await Promise.all(batch.map(task => task()));
            results.push(...batchResults);
        }
        return results;
    }
    async getCacheMemoryInfo() {
        return {
            usagePercentage: 50,
            totalMemory: 1024 * 1024 * 1024,
            usedMemory: 512 * 1024 * 1024,
            keyCount: 10000
        };
    }
    async performIntelligentEviction() {
        const patterns = ['search:*', 'temp:*', 'session:*'];
        for (const pattern of patterns) {
            const deletedKeys = await extendedCacheService.invalidatePattern(pattern);
            logger.info(`Evicted ${deletedKeys} keys matching pattern: ${pattern}`);
        }
    }
    async cleanupExpiredKeys() {
        logger.debug('Cleaning up expired cache keys');
    }
    async optimizeKeyPatterns() {
        logger.debug('Optimizing cache key patterns');
    }
    async analyzeAccessPatterns(userId) {
        return [];
    }
    generateCachePredictions(patterns, context) {
        return [];
    }
    async preloadData(key) {
        logger.debug('Preloading cache data', { key });
    }
    l1Cache = new Map();
    getFromL1Cache(key) {
        const item = this.l1Cache.get(key);
        if (!item)
            return null;
        if (item.expiry < new Date()) {
            this.l1Cache.delete(key);
            return null;
        }
        return item.value;
    }
    setL1Cache(key, value, ttlSeconds) {
        const expiry = new Date(Date.now() + ttlSeconds * 1000);
        this.l1Cache.set(key, { value, expiry });
        if (this.l1Cache.size > 1000) {
            const oldestKey = this.l1Cache.keys().next().value;
            this.l1Cache.delete(oldestKey);
        }
    }
    recordCacheHit(key, layer) {
        logger.debug('Cache hit', { key, layer });
    }
    recordCacheMiss(key) {
        logger.debug('Cache miss', { key });
    }
    getDefaultMetrics() {
        return {
            hits: 0,
            misses: 0,
            sets: 0,
            deletes: 0,
            hitRate: 0,
            averageResponseTime: 0,
            memoryUsage: 0,
            keyCount: 0,
        };
    }
    generateCacheRecommendations(analytics) {
        const recommendations = [];
        analytics.forEach(strategy => {
            if (strategy.performance.hitRate < 50) {
                recommendations.push(`Low hit rate for ${strategy.name} strategy - consider increasing TTL`);
            }
            if (strategy.performance.averageResponseTime > 100) {
                recommendations.push(`High response time for ${strategy.name} - consider cache warming`);
            }
        });
        return recommendations;
    }
    cleanupMetrics() {
        logger.debug('Cleaning up cache metrics');
    }
}
export const cacheOptimizer = CacheOptimizer.getInstance();
export const createOptimizedCacheKey = (prefix, ...parts) => {
    return `${prefix}:${parts.filter(p => p != null).join(':')}`;
};
export const getCacheStrategy = (dataType) => {
    return cacheOptimizer['strategies'].get(dataType);
};
//# sourceMappingURL=cacheOptimizer.js.map