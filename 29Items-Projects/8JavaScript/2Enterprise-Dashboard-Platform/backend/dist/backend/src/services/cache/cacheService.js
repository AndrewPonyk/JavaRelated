import { CacheService as BaseCacheService, redis } from '@/config/redis.js';
import { logger, logCacheOperation } from '@/utils/logger.js';
import { CacheError } from '@/utils/errors.js';
export class ExtendedCacheService extends BaseCacheService {
    CACHE_PREFIXES = {
        USER: 'user',
        DASHBOARD: 'dashboard',
        WIDGET: 'widget',
        ANALYTICS: 'analytics',
        SESSION: 'session',
        RATE_LIMIT: 'rate_limit',
        SEARCH: 'search',
        EXPORT: 'export',
    };
    DEFAULT_TTLS = {
        USER: 300,
        DASHBOARD: 600,
        WIDGET: 300,
        ANALYTICS: 900,
        SESSION: 3600,
        SEARCH: 1800,
        EXPORT: 3600,
    };
    async getUserById(userId) {
        const key = this.buildKey(this.CACHE_PREFIXES.USER, userId);
        logCacheOperation('get', key);
        return await this.get(key);
    }
    async setUser(userId, user, ttl) {
        const key = this.buildKey(this.CACHE_PREFIXES.USER, userId);
        const cacheTTL = ttl || this.DEFAULT_TTLS.USER;
        logCacheOperation('set', key);
        return await this.set(key, user, cacheTTL);
    }
    async invalidateUser(userId) {
        const key = this.buildKey(this.CACHE_PREFIXES.USER, userId);
        logCacheOperation('delete', key);
        return await this.delete(key);
    }
    async getDashboard(dashboardId) {
        const key = this.buildKey(this.CACHE_PREFIXES.DASHBOARD, dashboardId);
        logCacheOperation('get', key);
        return await this.get(key);
    }
    async setDashboard(dashboardId, dashboard, ttl) {
        const key = this.buildKey(this.CACHE_PREFIXES.DASHBOARD, dashboardId);
        const cacheTTL = ttl || this.DEFAULT_TTLS.DASHBOARD;
        logCacheOperation('set', key);
        return await this.set(key, dashboard, cacheTTL);
    }
    async invalidateDashboard(dashboardId) {
        const key = this.buildKey(this.CACHE_PREFIXES.DASHBOARD, dashboardId);
        logCacheOperation('delete', key);
        return await this.delete(key);
    }
    async getDashboardList(userId, queryHash) {
        const key = this.buildKey(this.CACHE_PREFIXES.DASHBOARD, 'list', userId, queryHash);
        logCacheOperation('get', key);
        return await this.get(key);
    }
    async setDashboardList(userId, queryHash, data, ttl) {
        const key = this.buildKey(this.CACHE_PREFIXES.DASHBOARD, 'list', userId, queryHash);
        const cacheTTL = ttl || this.DEFAULT_TTLS.DASHBOARD;
        logCacheOperation('set', key);
        return await this.set(key, data, cacheTTL);
    }
    async invalidateUserDashboards(userId) {
        const pattern = this.buildKey(this.CACHE_PREFIXES.DASHBOARD, 'list', userId, '*');
        logCacheOperation('invalidatePattern', pattern);
        return await this.invalidatePattern(pattern);
    }
    async getWidget(widgetId) {
        const key = this.buildKey(this.CACHE_PREFIXES.WIDGET, widgetId);
        logCacheOperation('get', key);
        return await this.get(key);
    }
    async setWidget(widgetId, widget, ttl) {
        const key = this.buildKey(this.CACHE_PREFIXES.WIDGET, widgetId);
        const cacheTTL = ttl || this.DEFAULT_TTLS.WIDGET;
        logCacheOperation('set', key);
        return await this.set(key, widget, cacheTTL);
    }
    async invalidateWidget(widgetId) {
        const key = this.buildKey(this.CACHE_PREFIXES.WIDGET, widgetId);
        logCacheOperation('delete', key);
        return await this.delete(key);
    }
    async getWidgetData(widgetId) {
        const key = this.buildKey(this.CACHE_PREFIXES.WIDGET, 'data', widgetId);
        logCacheOperation('get', key);
        return await this.get(key);
    }
    async setWidgetData(widgetId, data, ttl) {
        const key = this.buildKey(this.CACHE_PREFIXES.WIDGET, 'data', widgetId);
        const cacheTTL = ttl || this.DEFAULT_TTLS.WIDGET;
        logCacheOperation('set', key);
        return await this.set(key, data, cacheTTL);
    }
    async getAnalytics(dashboardId, queryHash) {
        const key = this.buildKey(this.CACHE_PREFIXES.ANALYTICS, dashboardId, queryHash);
        logCacheOperation('get', key);
        return await this.get(key);
    }
    async setAnalytics(dashboardId, queryHash, data, ttl) {
        const key = this.buildKey(this.CACHE_PREFIXES.ANALYTICS, dashboardId, queryHash);
        const cacheTTL = ttl || this.DEFAULT_TTLS.ANALYTICS;
        logCacheOperation('set', key);
        return await this.set(key, data, cacheTTL);
    }
    async invalidateAnalytics(dashboardId) {
        const pattern = this.buildKey(this.CACHE_PREFIXES.ANALYTICS, dashboardId, '*');
        logCacheOperation('invalidatePattern', pattern);
        return await this.invalidatePattern(pattern);
    }
    async getSearchResults(query, filters) {
        const queryHash = this.createHash({ query, filters });
        const key = this.buildKey(this.CACHE_PREFIXES.SEARCH, queryHash);
        logCacheOperation('get', key);
        return await this.get(key);
    }
    async setSearchResults(query, filters, results, ttl) {
        const queryHash = this.createHash({ query, filters });
        const key = this.buildKey(this.CACHE_PREFIXES.SEARCH, queryHash);
        const cacheTTL = ttl || this.DEFAULT_TTLS.SEARCH;
        logCacheOperation('set', key);
        return await this.set(key, results, cacheTTL);
    }
    async getSession(sessionId) {
        const key = this.buildKey(this.CACHE_PREFIXES.SESSION, sessionId);
        logCacheOperation('get', key);
        return await this.get(key);
    }
    async setSession(sessionId, sessionData, ttl) {
        const key = this.buildKey(this.CACHE_PREFIXES.SESSION, sessionId);
        const cacheTTL = ttl || this.DEFAULT_TTLS.SESSION;
        logCacheOperation('set', key);
        return await this.set(key, sessionData, cacheTTL);
    }
    async deleteSession(sessionId) {
        const key = this.buildKey(this.CACHE_PREFIXES.SESSION, sessionId);
        logCacheOperation('delete', key);
        return await this.delete(key);
    }
    async getRateLimit(identifier, window) {
        const key = this.buildKey(this.CACHE_PREFIXES.RATE_LIMIT, identifier, window);
        const count = await this.get(key);
        return count || 0;
    }
    async incrementRateLimit(identifier, window, ttl = 3600) {
        const key = this.buildKey(this.CACHE_PREFIXES.RATE_LIMIT, identifier, window);
        const newCount = await this.increment(key);
        await this.expire(key, ttl);
        return newCount;
    }
    async getExportStatus(exportId) {
        const key = this.buildKey(this.CACHE_PREFIXES.EXPORT, exportId);
        logCacheOperation('get', key);
        return await this.get(key);
    }
    async setExportStatus(exportId, status, ttl) {
        const key = this.buildKey(this.CACHE_PREFIXES.EXPORT, exportId);
        const cacheTTL = ttl || this.DEFAULT_TTLS.EXPORT;
        logCacheOperation('set', key);
        return await this.set(key, status, cacheTTL);
    }
    async invalidateUserData(userId) {
        try {
            await Promise.all([
                this.invalidateUser(userId),
                this.invalidateUserDashboards(userId),
                this.invalidatePattern(this.buildKey(this.CACHE_PREFIXES.WIDGET, '*', userId)),
                this.invalidatePattern(this.buildKey(this.CACHE_PREFIXES.SESSION, '*', userId)),
            ]);
            logger.info('Invalidated all user data from cache', { userId });
        }
        catch (error) {
            logger.error('Failed to invalidate user data', { userId, error });
            throw new CacheError(`Failed to invalidate user data: ${error}`);
        }
    }
    async invalidateDashboardData(dashboardId) {
        try {
            await Promise.all([
                this.invalidateDashboard(dashboardId),
                this.invalidateAnalytics(dashboardId),
                this.invalidatePattern(this.buildKey(this.CACHE_PREFIXES.WIDGET, 'data', '*')),
            ]);
            logger.info('Invalidated all dashboard data from cache', { dashboardId });
        }
        catch (error) {
            logger.error('Failed to invalidate dashboard data', { dashboardId, error });
            throw new CacheError(`Failed to invalidate dashboard data: ${error}`);
        }
    }
    async warmUserCache(userId, userData) {
        try {
            await this.setUser(userId, userData);
            logger.debug('Warmed user cache', { userId });
        }
        catch (error) {
            logger.warn('Failed to warm user cache', { userId, error });
        }
    }
    async warmDashboardCache(dashboardId, dashboardData) {
        try {
            await this.setDashboard(dashboardId, dashboardData);
            logger.debug('Warmed dashboard cache', { dashboardId });
        }
        catch (error) {
            logger.warn('Failed to warm dashboard cache', { dashboardId, error });
        }
    }
    async getCacheStats() {
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
        }
        catch (error) {
            logger.error('Failed to get cache stats', { error });
            return null;
        }
    }
    async getCacheHitRate() {
        try {
            const info = await redis.info('stats');
            const stats = this.parseRedisInfo(info);
            const hits = parseInt(stats.keyspace_hits || '0');
            const misses = parseInt(stats.keyspace_misses || '0');
            const total = hits + misses;
            return total > 0 ? (hits / total) * 100 : 0;
        }
        catch (error) {
            logger.error('Failed to calculate cache hit rate', { error });
            return 0;
        }
    }
    async healthCheck() {
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
        }
        catch (error) {
            return {
                status: 'unhealthy',
                details: {
                    error: error instanceof Error ? error.message : 'Unknown error',
                },
            };
        }
    }
    buildKey(...parts) {
        return parts.filter(Boolean).join(':');
    }
    createHash(obj) {
        const str = JSON.stringify(obj, Object.keys(obj).sort());
        let hash = 0;
        for (let i = 0; i < str.length; i++) {
            const char = str.charCodeAt(i);
            hash = ((hash << 5) - hash) + char;
            hash = hash & hash;
        }
        return Math.abs(hash).toString(16);
    }
    parseRedisInfo(info) {
        const result = {};
        info.split('\r\n').forEach(line => {
            if (line.includes(':')) {
                const [key, value] = line.split(':');
                result[key] = value;
            }
        });
        return result;
    }
    createCacheMiddleware(keyGenerator, ttl = 300, condition) {
        return async (req, res, next) => {
            try {
                if (condition && !condition(req)) {
                    return next();
                }
                const key = keyGenerator(req);
                const cachedData = await this.get(key);
                if (cachedData) {
                    logCacheOperation('get', key, true);
                    return res.json(cachedData);
                }
                const originalJson = res.json.bind(res);
                res.json = (data) => {
                    if (res.statusCode >= 200 && res.statusCode < 300) {
                        this.set(key, data, ttl).catch(error => {
                            logger.warn('Failed to cache response', { key, error });
                        });
                    }
                    return originalJson(data);
                };
                logCacheOperation('get', key, false);
                next();
            }
            catch (error) {
                logger.warn('Cache middleware error', { error });
                next();
            }
        };
    }
}
export const extendedCacheService = new ExtendedCacheService();
export { extendedCacheService as CacheService };
//# sourceMappingURL=cacheService.js.map