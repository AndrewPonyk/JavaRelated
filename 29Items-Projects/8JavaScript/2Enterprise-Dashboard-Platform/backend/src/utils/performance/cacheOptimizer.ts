import { redis, redisPublisher, redisSubscriber } from '@/config/redis.js';
import { extendedCacheService } from '@/services/cache/cacheService.js';
import { logger } from '@/utils/logger.js';
import { config } from '@/config/environment.js';

/**
 * Advanced Cache Optimizer
 * Provides intelligent caching strategies, cache warming, and distributed cache invalidation
 */

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
  warmupInterval: number; // minutes
}

export class CacheOptimizer {
  private static instance: CacheOptimizer;
  private metrics: Map<string, CacheMetrics> = new Map();
  private warmupConfig: CacheWarmupConfig;
  private strategies: Map<string, CacheStrategy> = new Map();

  // Cache invalidation queue for distributed systems
  private invalidationQueue: Array<{
    pattern: string;
    timestamp: Date;
    source: string;
  }> = [];

  private constructor() {
    this.warmupConfig = {
      enabled: config.app.env === 'production',
      strategies: {
        user: true,
        dashboard: true,
        analytics: true,
        search: false, // Search results are too dynamic
      },
      batchSize: 50,
      maxConcurrency: 5,
      warmupInterval: 60, // 1 hour
    };

    this.initializeCacheStrategies();
    this.setupDistributedInvalidation();
    this.startBackgroundTasks();
  }

  public static getInstance(): CacheOptimizer {
    if (!CacheOptimizer.instance) {
      CacheOptimizer.instance = new CacheOptimizer();
    }
    return CacheOptimizer.instance;
  }

  private initializeCacheStrategies(): void {
    // Define optimal caching strategies for different data types
    this.strategies.set('user-profile', {
      name: 'user-profile',
      ttl: 1800, // 30 minutes
      maxSize: 10000,
      warmupEnabled: true,
      preloadPatterns: ['user:*:profile'],
      invalidationStrategy: 'immediate',
    });

    this.strategies.set('dashboard-metadata', {
      name: 'dashboard-metadata',
      ttl: 900, // 15 minutes
      maxSize: 5000,
      warmupEnabled: true,
      preloadPatterns: ['dashboard:*:metadata'],
      invalidationStrategy: 'immediate',
    });

    this.strategies.set('dashboard-data', {
      name: 'dashboard-data',
      ttl: 300, // 5 minutes
      maxSize: 2000,
      warmupEnabled: false, // Too dynamic
      invalidationStrategy: 'lazy',
    });

    this.strategies.set('analytics-aggregated', {
      name: 'analytics-aggregated',
      ttl: 3600, // 1 hour
      maxSize: 1000,
      warmupEnabled: true,
      preloadPatterns: ['analytics:*:daily', 'analytics:*:weekly'],
      invalidationStrategy: 'scheduled',
    });

    this.strategies.set('search-results', {
      name: 'search-results',
      ttl: 600, // 10 minutes
      maxSize: 500,
      warmupEnabled: false, // Too query-dependent
      invalidationStrategy: 'lazy',
    });
  }

  /**
   * Intelligent cache warming based on usage patterns
   */
  public async warmupCache(force: boolean = false): Promise<void> {
    if (!this.warmupConfig.enabled && !force) {
      logger.debug('Cache warmup disabled');
      return;
    }

    const startTime = Date.now();
    logger.info('Starting cache warmup process');

    try {
      // Warm up different data types in parallel with concurrency control
      const warmupTasks: Array<() => Promise<void>> = [];

      if (this.warmupConfig.strategies.user) {
        warmupTasks.push(() => this.warmupUserData());
      }

      if (this.warmupConfig.strategies.dashboard) {
        warmupTasks.push(() => this.warmupDashboardData());
      }

      if (this.warmupConfig.strategies.analytics) {
        warmupTasks.push(() => this.warmupAnalyticsData());
      }

      // Execute warmup tasks with controlled concurrency
      await this.executeConcurrently(warmupTasks, this.warmupConfig.maxConcurrency);

      const duration = Date.now() - startTime;
      logger.info('Cache warmup completed', {
        duration,
        tasksCompleted: warmupTasks.length
      });

      // Schedule next warmup
      if (!force) {
        setTimeout(() => {
          this.warmupCache(false);
        }, this.warmupConfig.warmupInterval * 60 * 1000);
      }

    } catch (error) {
      logger.error('Cache warmup failed', { error });
    }
  }

  /**
   * Advanced cache eviction based on LRU and usage patterns
   */
  public async optimizeMemoryUsage(): Promise<void> {
    try {
      const memoryInfo = await this.getCacheMemoryInfo();

      if (memoryInfo.usagePercentage > 80) {
        logger.warn('High cache memory usage detected', memoryInfo);

        // Implement intelligent eviction
        await this.performIntelligentEviction();
      }

      // Clean up expired keys
      await this.cleanupExpiredKeys();

      // Optimize key patterns
      await this.optimizeKeyPatterns();

    } catch (error) {
      logger.error('Memory optimization failed', { error });
    }
  }

  /**
   * Distributed cache invalidation for multi-instance deployments
   */
  public async invalidateDistributed(pattern: string, source: string = 'local'): Promise<void> {
    try {
      // Add to local invalidation queue
      this.invalidationQueue.push({
        pattern,
        timestamp: new Date(),
        source,
      });

      // Publish invalidation event to other instances
      await redisPublisher.publish('cache:invalidate', JSON.stringify({
        pattern,
        timestamp: new Date().toISOString(),
        source,
      }));

      // Perform local invalidation
      const deletedKeys = await extendedCacheService.invalidatePattern(pattern);

      logger.info('Distributed cache invalidation completed', {
        pattern,
        deletedKeys,
        source
      });

    } catch (error) {
      logger.error('Distributed cache invalidation failed', { error, pattern });
    }
  }

  /**
   * Predictive caching based on access patterns
   */
  public async performPredictiveCaching(userId: string, patterns: string[]): Promise<void> {
    try {
      // Analyze user's access patterns from metrics
      const accessPatterns = await this.analyzeAccessPatterns(userId);

      // Predict likely next requests
      const predictions = this.generateCachePredictions(accessPatterns, patterns);

      // Preload predicted data
      for (const prediction of predictions) {
        await this.preloadData(prediction);
      }

      logger.debug('Predictive caching completed', {
        userId,
        predictions: predictions.length
      });

    } catch (error) {
      logger.error('Predictive caching failed', { error, userId });
    }
  }

  /**
   * Multi-layer cache with L1 (memory) and L2 (Redis)
   */
  public async getMultiLayer<T>(
    key: string,
    fetcher: () => Promise<T>,
    options: {
      l1TTL?: number;
      l2TTL?: number;
      strategy?: string;
    } = {}
  ): Promise<T> {
    const { l1TTL = 60, l2TTL = 300, strategy = 'default' } = options;

    try {
      // Try L1 cache first (in-memory)
      const l1Value = this.getFromL1Cache<T>(key);
      if (l1Value !== null) {
        this.recordCacheHit(key, 'L1');
        return l1Value;
      }

      // Try L2 cache (Redis)
      const l2Value = await extendedCacheService.get<T>(key);
      if (l2Value !== null) {
        // Store in L1 for faster access
        this.setL1Cache(key, l2Value, l1TTL);
        this.recordCacheHit(key, 'L2');
        return l2Value;
      }

      // Cache miss - fetch from source
      const value = await fetcher();

      // Store in both layers
      this.setL1Cache(key, value, l1TTL);
      await extendedCacheService.set(key, value, l2TTL);

      this.recordCacheMiss(key);
      return value;

    } catch (error) {
      logger.error('Multi-layer cache get failed', { error, key });
      throw error;
    }
  }

  /**
   * Batch cache operations for better performance
   */
  public async batchGet<T>(keys: string[]): Promise<Map<string, T | null>> {
    const results = new Map<string, T | null>();

    try {
      // Get all values in a single Redis call
      const values = await redis.mget(...keys);

      keys.forEach((key, index) => {
        const value = values[index];
        if (value) {
          try {
            results.set(key, JSON.parse(value));
          } catch {
            results.set(key, null);
          }
        } else {
          results.set(key, null);
        }
      });

      return results;

    } catch (error) {
      logger.error('Batch get failed', { error, keyCount: keys.length });

      // Fallback to individual gets
      for (const key of keys) {
        try {
          const value = await extendedCacheService.get<T>(key);
          results.set(key, value);
        } catch {
          results.set(key, null);
        }
      }

      return results;
    }
  }

  /**
   * Cache analytics and performance metrics
   */
  public getCacheAnalytics(): {
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
  } {
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

    // Calculate overall metrics
    const totalHits = Array.from(this.metrics.values()).reduce((sum, m) => sum + m.hits, 0);
    const totalMisses = Array.from(this.metrics.values()).reduce((sum, m) => sum + m.misses, 0);
    const overallHitRate = totalHits + totalMisses > 0
      ? (totalHits / (totalHits + totalMisses)) * 100
      : 0;

    // Generate recommendations
    const recommendations = this.generateCacheRecommendations(strategyAnalytics);

    return {
      strategies: strategyAnalytics,
      overall: {
        totalHits,
        totalMisses,
        overallHitRate,
        memoryUsage: 0, // Would need Redis INFO command
        recommendations
      }
    };
  }

  // Private methods

  private setupDistributedInvalidation(): void {
    // Subscribe to cache invalidation events from other instances
    redisSubscriber.subscribe('cache:invalidate');

    redisSubscriber.on('message', async (channel: string, message: string) => {
      if (channel === 'cache:invalidate') {
        try {
          const event = JSON.parse(message);

          // Don't process our own invalidations
          if (event.source === 'local') return;

          await extendedCacheService.invalidatePattern(event.pattern);

          logger.debug('Processed distributed cache invalidation', {
            pattern: event.pattern,
            source: event.source
          });

        } catch (error) {
          logger.error('Failed to process cache invalidation event', { error, message });
        }
      }
    });
  }

  private startBackgroundTasks(): void {
    // Memory optimization every 30 minutes
    setInterval(() => {
      this.optimizeMemoryUsage();
    }, 30 * 60 * 1000);

    // Cache warmup based on configuration
    if (this.warmupConfig.enabled) {
      setTimeout(() => {
        this.warmupCache(false);
      }, 5 * 60 * 1000); // Start after 5 minutes
    }

    // Metrics cleanup every hour
    setInterval(() => {
      this.cleanupMetrics();
    }, 60 * 60 * 1000);
  }

  private async warmupUserData(): Promise<void> {
    // Implement user data warmup logic
    logger.debug('Warming up user data cache');
    // This would fetch frequently accessed user profiles
  }

  private async warmupDashboardData(): Promise<void> {
    // Implement dashboard data warmup logic
    logger.debug('Warming up dashboard data cache');
    // This would fetch popular dashboards and their metadata
  }

  private async warmupAnalyticsData(): Promise<void> {
    // Implement analytics data warmup logic
    logger.debug('Warming up analytics data cache');
    // This would pre-calculate common analytics queries
  }

  private async executeConcurrently<T>(
    tasks: Array<() => Promise<T>>,
    maxConcurrency: number
  ): Promise<T[]> {
    const results: T[] = [];

    for (let i = 0; i < tasks.length; i += maxConcurrency) {
      const batch = tasks.slice(i, i + maxConcurrency);
      const batchResults = await Promise.all(batch.map(task => task()));
      results.push(...batchResults);
    }

    return results;
  }

  private async getCacheMemoryInfo(): Promise<{
    usagePercentage: number;
    totalMemory: number;
    usedMemory: number;
    keyCount: number;
  }> {
    // Would implement Redis INFO parsing
    return {
      usagePercentage: 50, // Placeholder
      totalMemory: 1024 * 1024 * 1024, // 1GB
      usedMemory: 512 * 1024 * 1024, // 512MB
      keyCount: 10000
    };
  }

  private async performIntelligentEviction(): Promise<void> {
    // Implement LRU-based eviction with usage patterns
    const patterns = ['search:*', 'temp:*', 'session:*'];

    for (const pattern of patterns) {
      const deletedKeys = await extendedCacheService.invalidatePattern(pattern);
      logger.info(`Evicted ${deletedKeys} keys matching pattern: ${pattern}`);
    }
  }

  private async cleanupExpiredKeys(): Promise<void> {
    // Redis handles TTL automatically, but we can optimize key patterns
    logger.debug('Cleaning up expired cache keys');
  }

  private async optimizeKeyPatterns(): Promise<void> {
    // Analyze and optimize key naming patterns for better performance
    logger.debug('Optimizing cache key patterns');
  }

  private async analyzeAccessPatterns(userId: string): Promise<string[]> {
    // Analyze user's historical access patterns
    return []; // Placeholder
  }

  private generateCachePredictions(patterns: string[], context: string[]): string[] {
    // Generate predictive cache keys based on patterns
    return []; // Placeholder
  }

  private async preloadData(key: string): Promise<void> {
    // Preload data based on predictions
    logger.debug('Preloading cache data', { key });
  }

  // L1 (in-memory) cache implementation
  private l1Cache = new Map<string, { value: any; expiry: Date }>();

  private getFromL1Cache<T>(key: string): T | null {
    const item = this.l1Cache.get(key);
    if (!item) return null;

    if (item.expiry < new Date()) {
      this.l1Cache.delete(key);
      return null;
    }

    return item.value;
  }

  private setL1Cache(key: string, value: any, ttlSeconds: number): void {
    const expiry = new Date(Date.now() + ttlSeconds * 1000);
    this.l1Cache.set(key, { value, expiry });

    // Limit L1 cache size
    if (this.l1Cache.size > 1000) {
      const oldestKey = this.l1Cache.keys().next().value;
      this.l1Cache.delete(oldestKey);
    }
  }

  private recordCacheHit(key: string, layer: string): void {
    // Record cache hit metrics
    logger.debug('Cache hit', { key, layer });
  }

  private recordCacheMiss(key: string): void {
    // Record cache miss metrics
    logger.debug('Cache miss', { key });
  }

  private getDefaultMetrics(): CacheMetrics {
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

  private generateCacheRecommendations(analytics: any[]): string[] {
    const recommendations: string[] = [];

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

  private cleanupMetrics(): void {
    // Clean up old metrics to prevent memory leaks
    logger.debug('Cleaning up cache metrics');
  }
}

// Export singleton instance
export const cacheOptimizer = CacheOptimizer.getInstance();

// Export utility functions
export const createOptimizedCacheKey = (prefix: string, ...parts: Array<string | number>): string => {
  return `${prefix}:${parts.filter(p => p != null).join(':')}`;
};

export const getCacheStrategy = (dataType: string): CacheStrategy | undefined => {
  return cacheOptimizer['strategies'].get(dataType);
};