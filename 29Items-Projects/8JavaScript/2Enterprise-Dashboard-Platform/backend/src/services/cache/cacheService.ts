import { CacheService as BaseCacheService, redis } from '@/config/redis.js';
import { logger, logCacheOperation } from '@/utils/logger.js';
import { CacheError } from '@/utils/errors.js';

// Extended cache service with business logic specific caching
export class ExtendedCacheService extends BaseCacheService {
  private readonly CACHE_PREFIXES = {
    USER: 'user',
    DASHBOARD: 'dashboard',
    WIDGET: 'widget',
    ANALYTICS: 'analytics',
    SESSION: 'session',
    RATE_LIMIT: 'rate_limit',
    SEARCH: 'search',
    EXPORT: 'export',
    DATA_CONNECTION: 'data_connection',
    QUERY_RESULT: 'query_result',
    SCHEMA: 'schema',
  } as const;

  private readonly DEFAULT_TTLS = {
    USER: 300, // 5 minutes
    DASHBOARD: 600, // 10 minutes
    WIDGET: 300, // 5 minutes
    ANALYTICS: 900, // 15 minutes
    SESSION: 3600, // 1 hour
    SEARCH: 1800, // 30 minutes
    EXPORT: 3600, // 1 hour
    DATA_CONNECTION: 600, // 10 minutes
    QUERY_RESULT: 300, // 5 minutes (default, configurable per query)
    SCHEMA: 1800, // 30 minutes
  } as const;

  // User caching methods
  async getUserById(userId: string): Promise<any | null> {
    const key = this.buildKey(this.CACHE_PREFIXES.USER, userId);
    logCacheOperation('get', key);
    return await this.get(key);
  }

  async setUser(userId: string, user: any, ttl?: number): Promise<boolean> {
    const key = this.buildKey(this.CACHE_PREFIXES.USER, userId);
    const cacheTTL = ttl || this.DEFAULT_TTLS.USER;
    logCacheOperation('set', key);
    return await this.set(key, user, cacheTTL);
  }

  async invalidateUser(userId: string): Promise<boolean> {
    const key = this.buildKey(this.CACHE_PREFIXES.USER, userId);
    logCacheOperation('delete', key);
    return await this.delete(key);
  }

  // Dashboard caching methods
  async getDashboard(dashboardId: string): Promise<any | null> {
    const key = this.buildKey(this.CACHE_PREFIXES.DASHBOARD, dashboardId);
    logCacheOperation('get', key);
    return await this.get(key);
  }

  async setDashboard(dashboardId: string, dashboard: any, ttl?: number): Promise<boolean> {
    const key = this.buildKey(this.CACHE_PREFIXES.DASHBOARD, dashboardId);
    const cacheTTL = ttl || this.DEFAULT_TTLS.DASHBOARD;
    logCacheOperation('set', key);
    return await this.set(key, dashboard, cacheTTL);
  }

  async invalidateDashboard(dashboardId: string): Promise<boolean> {
    const key = this.buildKey(this.CACHE_PREFIXES.DASHBOARD, dashboardId);
    logCacheOperation('delete', key);
    return await this.delete(key);
  }

  // Dashboard list caching (with pagination)
  async getDashboardList(userId: string, queryHash: string): Promise<any | null> {
    const key = this.buildKey(this.CACHE_PREFIXES.DASHBOARD, 'list', userId, queryHash);
    logCacheOperation('get', key);
    return await this.get(key);
  }

  async setDashboardList(userId: string, queryHash: string, data: any, ttl?: number): Promise<boolean> {
    const key = this.buildKey(this.CACHE_PREFIXES.DASHBOARD, 'list', userId, queryHash);
    const cacheTTL = ttl || this.DEFAULT_TTLS.DASHBOARD;
    logCacheOperation('set', key);
    return await this.set(key, data, cacheTTL);
  }

  async invalidateUserDashboards(userId: string): Promise<number> {
    const pattern = this.buildKey(this.CACHE_PREFIXES.DASHBOARD, 'list', userId, '*');
    logCacheOperation('invalidatePattern', pattern);
    return await this.invalidatePattern(pattern);
  }

  // Widget caching methods
  async getWidget(widgetId: string): Promise<any | null> {
    const key = this.buildKey(this.CACHE_PREFIXES.WIDGET, widgetId);
    logCacheOperation('get', key);
    return await this.get(key);
  }

  async setWidget(widgetId: string, widget: any, ttl?: number): Promise<boolean> {
    const key = this.buildKey(this.CACHE_PREFIXES.WIDGET, widgetId);
    const cacheTTL = ttl || this.DEFAULT_TTLS.WIDGET;
    logCacheOperation('set', key);
    return await this.set(key, widget, cacheTTL);
  }

  async invalidateWidget(widgetId: string): Promise<boolean> {
    const key = this.buildKey(this.CACHE_PREFIXES.WIDGET, widgetId);
    logCacheOperation('delete', key);
    return await this.delete(key);
  }

  // Widget data caching (separate from widget metadata)
  async getWidgetData(widgetId: string): Promise<any | null> {
    const key = this.buildKey(this.CACHE_PREFIXES.WIDGET, 'data', widgetId);
    logCacheOperation('get', key);
    return await this.get(key);
  }

  async setWidgetData(widgetId: string, data: any, ttl?: number): Promise<boolean> {
    const key = this.buildKey(this.CACHE_PREFIXES.WIDGET, 'data', widgetId);
    const cacheTTL = ttl || this.DEFAULT_TTLS.WIDGET;
    logCacheOperation('set', key);
    return await this.set(key, data, cacheTTL);
  }

  // Analytics caching methods
  async getAnalytics(dashboardId: string, queryHash: string): Promise<any | null> {
    const key = this.buildKey(this.CACHE_PREFIXES.ANALYTICS, dashboardId, queryHash);
    logCacheOperation('get', key);
    return await this.get(key);
  }

  async setAnalytics(dashboardId: string, queryHash: string, data: any, ttl?: number): Promise<boolean> {
    const key = this.buildKey(this.CACHE_PREFIXES.ANALYTICS, dashboardId, queryHash);
    const cacheTTL = ttl || this.DEFAULT_TTLS.ANALYTICS;
    logCacheOperation('set', key);
    return await this.set(key, data, cacheTTL);
  }

  async invalidateAnalytics(dashboardId: string): Promise<number> {
    const pattern = this.buildKey(this.CACHE_PREFIXES.ANALYTICS, dashboardId, '*');
    logCacheOperation('invalidatePattern', pattern);
    return await this.invalidatePattern(pattern);
  }

  // Search results caching
  async getSearchResults(query: string, filters: Record<string, any>): Promise<any | null> {
    const queryHash = this.createHash({ query, filters });
    const key = this.buildKey(this.CACHE_PREFIXES.SEARCH, queryHash);
    logCacheOperation('get', key);
    return await this.get(key);
  }

  async setSearchResults(query: string, filters: Record<string, any>, results: any, ttl?: number): Promise<boolean> {
    const queryHash = this.createHash({ query, filters });
    const key = this.buildKey(this.CACHE_PREFIXES.SEARCH, queryHash);
    const cacheTTL = ttl || this.DEFAULT_TTLS.SEARCH;
    logCacheOperation('set', key);
    return await this.set(key, results, cacheTTL);
  }

  // Session caching
  async getSession(sessionId: string): Promise<any | null> {
    const key = this.buildKey(this.CACHE_PREFIXES.SESSION, sessionId);
    logCacheOperation('get', key);
    return await this.get(key);
  }

  async setSession(sessionId: string, sessionData: any, ttl?: number): Promise<boolean> {
    const key = this.buildKey(this.CACHE_PREFIXES.SESSION, sessionId);
    const cacheTTL = ttl || this.DEFAULT_TTLS.SESSION;
    logCacheOperation('set', key);
    return await this.set(key, sessionData, cacheTTL);
  }

  async deleteSession(sessionId: string): Promise<boolean> {
    const key = this.buildKey(this.CACHE_PREFIXES.SESSION, sessionId);
    logCacheOperation('delete', key);
    return await this.delete(key);
  }

  // Rate limiting
  async getRateLimit(identifier: string, window: string): Promise<number> {
    const key = this.buildKey(this.CACHE_PREFIXES.RATE_LIMIT, identifier, window);
    const count = await this.get<number>(key);
    return count || 0;
  }

  async incrementRateLimit(identifier: string, window: string, ttl: number = 3600): Promise<number> {
    const key = this.buildKey(this.CACHE_PREFIXES.RATE_LIMIT, identifier, window);
    const newCount = await this.increment(key);
    await this.expire(key, ttl);
    return newCount;
  }

  // Export status caching
  async getExportStatus(exportId: string): Promise<any | null> {
    const key = this.buildKey(this.CACHE_PREFIXES.EXPORT, exportId);
    logCacheOperation('get', key);
    return await this.get(key);
  }

  async setExportStatus(exportId: string, status: any, ttl?: number): Promise<boolean> {
    const key = this.buildKey(this.CACHE_PREFIXES.EXPORT, exportId);
    const cacheTTL = ttl || this.DEFAULT_TTLS.EXPORT;
    logCacheOperation('set', key);
    return await this.set(key, status, cacheTTL);
  }

  // ============================================================
  // Data Connection Caching Methods
  // ============================================================

  // Query result caching
  async getQueryResult(cacheKey: string): Promise<any | null> {
    const key = this.buildKey(this.CACHE_PREFIXES.QUERY_RESULT, cacheKey);
    logCacheOperation('get', key);
    return await this.get(key);
  }

  async setQueryResult(cacheKey: string, result: any, ttl?: number): Promise<boolean> {
    const key = this.buildKey(this.CACHE_PREFIXES.QUERY_RESULT, cacheKey);
    const cacheTTL = ttl || this.DEFAULT_TTLS.QUERY_RESULT;
    logCacheOperation('set', key);
    return await this.set(key, result, cacheTTL);
  }

  async invalidateQueryResults(connectionId: string): Promise<number> {
    const pattern = this.buildKey(this.CACHE_PREFIXES.QUERY_RESULT, `query:${connectionId}:*`);
    logCacheOperation('invalidatePattern', pattern);
    return await this.invalidatePattern(pattern);
  }

  // Schema caching (longer TTL)
  async getSchema(cacheKey: string): Promise<any | null> {
    const key = this.buildKey(this.CACHE_PREFIXES.SCHEMA, cacheKey);
    logCacheOperation('get', key);
    return await this.get(key);
  }

  async setSchema(cacheKey: string, schema: any, ttl?: number): Promise<boolean> {
    const key = this.buildKey(this.CACHE_PREFIXES.SCHEMA, cacheKey);
    const cacheTTL = ttl || this.DEFAULT_TTLS.SCHEMA;
    logCacheOperation('set', key);
    return await this.set(key, schema, cacheTTL);
  }

  async invalidateSchema(connectionId: string): Promise<boolean> {
    const key = this.buildKey(this.CACHE_PREFIXES.SCHEMA, `schema:${connectionId}`);
    logCacheOperation('delete', key);
    return await this.delete(key);
  }

  // Data connection caching
  async getDataConnection(connectionId: string): Promise<any | null> {
    const key = this.buildKey(this.CACHE_PREFIXES.DATA_CONNECTION, connectionId);
    logCacheOperation('get', key);
    return await this.get(key);
  }

  async setDataConnection(connectionId: string, connection: any, ttl?: number): Promise<boolean> {
    const key = this.buildKey(this.CACHE_PREFIXES.DATA_CONNECTION, connectionId);
    const cacheTTL = ttl || this.DEFAULT_TTLS.DATA_CONNECTION;
    logCacheOperation('set', key);
    return await this.set(key, connection, cacheTTL);
  }

  async invalidateDataConnection(connectionId: string): Promise<void> {
    try {
      await Promise.all([
        this.delete(this.buildKey(this.CACHE_PREFIXES.DATA_CONNECTION, connectionId)),
        this.invalidateQueryResults(connectionId),
        this.invalidateSchema(connectionId),
      ]);
      logger.info('Invalidated data connection cache', { connectionId });
    } catch (error) {
      logger.error('Failed to invalidate data connection cache', { connectionId, error });
    }
  }

  // Batch operations
  async invalidateUserData(userId: string): Promise<void> {
    try {
      await Promise.all([
        this.invalidateUser(userId),
        this.invalidateUserDashboards(userId),
        this.invalidatePattern(this.buildKey(this.CACHE_PREFIXES.WIDGET, '*', userId)),
        this.invalidatePattern(this.buildKey(this.CACHE_PREFIXES.SESSION, '*', userId)),
      ]);

      logger.info('Invalidated all user data from cache', { userId });
    } catch (error) {
      logger.error('Failed to invalidate user data', { userId, error });
      throw new CacheError(`Failed to invalidate user data: ${error}`);
    }
  }

  async invalidateDashboardData(dashboardId: string): Promise<void> {
    try {
      await Promise.all([
        this.invalidateDashboard(dashboardId),
        this.invalidateAnalytics(dashboardId),
        this.invalidatePattern(this.buildKey(this.CACHE_PREFIXES.WIDGET, 'data', '*')),
      ]);

      logger.info('Invalidated all dashboard data from cache', { dashboardId });
    } catch (error) {
      logger.error('Failed to invalidate dashboard data', { dashboardId, error });
      throw new CacheError(`Failed to invalidate dashboard data: ${error}`);
    }
  }

  // Cache warming methods
  async warmUserCache(userId: string, userData: any): Promise<void> {
    try {
      await this.setUser(userId, userData);
      logger.debug('Warmed user cache', { userId });
    } catch (error) {
      logger.warn('Failed to warm user cache', { userId, error });
    }
  }

  async warmDashboardCache(dashboardId: string, dashboardData: any): Promise<void> {
    try {
      await this.setDashboard(dashboardId, dashboardData);
      logger.debug('Warmed dashboard cache', { dashboardId });
    } catch (error) {
      logger.warn('Failed to warm dashboard cache', { dashboardId, error });
    }
  }

  // Cache statistics and monitoring
  async getCacheStats(): Promise<any> {
    try {
      const info = await redis.info('memory');
      const keyspace = await redis.info('keyspace');
      const stats = await redis.info('stats');

      return {
        memory: this.parseRedisInfo(info),
        keyspace: this.parseRedisInfo(keyspace),
        stats: this.parseRedisInfo(stats),
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      logger.error('Failed to get cache stats', { error });
      return null;
    }
  }

  async getCacheHitRate(): Promise<number> {
    try {
      const info = await redis.info('stats');
      const stats = this.parseRedisInfo(info);

      const hits = parseInt(stats.keyspace_hits || '0');
      const misses = parseInt(stats.keyspace_misses || '0');
      const total = hits + misses;

      return total > 0 ? (hits / total) * 100 : 0;
    } catch (error) {
      logger.error('Failed to calculate cache hit rate', { error });
      return 0;
    }
  }

  // Health check
  async healthCheck(): Promise<{ status: 'healthy' | 'unhealthy'; details: any }> {
    try {
      const start = Date.now();
      await redis.ping();
      const latency = Date.now() - start;

      const stats = await this.getCacheStats();
      const hitRate = await this.getCacheHitRate();

      return {
        status: 'healthy',
        details: {
          latency: `${latency}ms`,
          hitRate: `${hitRate.toFixed(2)}%`,
          memory: stats?.memory,
        },
      };
    } catch (error) {
      return {
        status: 'unhealthy',
        details: {
          error: error instanceof Error ? error.message : 'Unknown error',
        },
      };
    }
  }

  // Helper methods
  private buildKey(...parts: string[]): string {
    return parts.filter(Boolean).join(':');
  }

  private createHash(obj: any): string {
    const str = JSON.stringify(obj, Object.keys(obj).sort());
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
      const char = str.charCodeAt(i);
      hash = ((hash << 5) - hash) + char;
      hash = hash & hash; // Convert to 32-bit integer
    }
    return Math.abs(hash).toString(16);
  }

  private parseRedisInfo(info: string): Record<string, string> {
    const result: Record<string, string> = {};
    info.split('\r\n').forEach(line => {
      if (line.includes(':')) {
        const [key, value] = line.split(':');
        if (key) {
          result[key] = value || '';
        }
      }
    });
    return result;
  }

  // Cache middleware for Express routes
  public createCacheMiddleware(
    keyGenerator: (req: any) => string,
    ttl: number = 300,
    condition?: (req: any) => boolean
  ) {
    return async (req: any, res: any, next: any) => {
      try {
        // Skip caching if condition fails
        if (condition && !condition(req)) {
          return next();
        }

        const key = keyGenerator(req);
        const cachedData = await this.get(key);

        if (cachedData) {
          logCacheOperation('get', key, true);
          return res.json(cachedData);
        }

        // Store original res.json to capture response
        const originalJson = res.json.bind(res);
        res.json = (data: any) => {
          // Cache successful responses
          if (res.statusCode >= 200 && res.statusCode < 300) {
            this.set(key, data, ttl).catch(error => {
              logger.warn('Failed to cache response', { key, error });
            });
          }
          return originalJson(data);
        };

        logCacheOperation('get', key, false);
        next();
      } catch (error) {
        logger.warn('Cache middleware error', { error });
        next();
      }
    };
  }
}

// Create and export extended cache service instance
export const extendedCacheService = new ExtendedCacheService();

// Export for backward compatibility
export { extendedCacheService as CacheService };