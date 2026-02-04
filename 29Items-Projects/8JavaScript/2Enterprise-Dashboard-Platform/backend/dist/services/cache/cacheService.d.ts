import { CacheService as BaseCacheService } from '@/config/redis.js';
export declare class ExtendedCacheService extends BaseCacheService {
    private readonly CACHE_PREFIXES;
    private readonly DEFAULT_TTLS;
    getUserById(userId: string): Promise<any | null>;
    setUser(userId: string, user: any, ttl?: number): Promise<boolean>;
    invalidateUser(userId: string): Promise<boolean>;
    getDashboard(dashboardId: string): Promise<any | null>;
    setDashboard(dashboardId: string, dashboard: any, ttl?: number): Promise<boolean>;
    invalidateDashboard(dashboardId: string): Promise<boolean>;
    getDashboardList(userId: string, queryHash: string): Promise<any | null>;
    setDashboardList(userId: string, queryHash: string, data: any, ttl?: number): Promise<boolean>;
    invalidateUserDashboards(userId: string): Promise<number>;
    getWidget(widgetId: string): Promise<any | null>;
    setWidget(widgetId: string, widget: any, ttl?: number): Promise<boolean>;
    invalidateWidget(widgetId: string): Promise<boolean>;
    getWidgetData(widgetId: string): Promise<any | null>;
    setWidgetData(widgetId: string, data: any, ttl?: number): Promise<boolean>;
    getAnalytics(dashboardId: string, queryHash: string): Promise<any | null>;
    setAnalytics(dashboardId: string, queryHash: string, data: any, ttl?: number): Promise<boolean>;
    invalidateAnalytics(dashboardId: string): Promise<number>;
    getSearchResults(query: string, filters: Record<string, any>): Promise<any | null>;
    setSearchResults(query: string, filters: Record<string, any>, results: any, ttl?: number): Promise<boolean>;
    getSession(sessionId: string): Promise<any | null>;
    setSession(sessionId: string, sessionData: any, ttl?: number): Promise<boolean>;
    deleteSession(sessionId: string): Promise<boolean>;
    getRateLimit(identifier: string, window: string): Promise<number>;
    incrementRateLimit(identifier: string, window: string, ttl?: number): Promise<number>;
    getExportStatus(exportId: string): Promise<any | null>;
    setExportStatus(exportId: string, status: any, ttl?: number): Promise<boolean>;
    invalidateUserData(userId: string): Promise<void>;
    invalidateDashboardData(dashboardId: string): Promise<void>;
    warmUserCache(userId: string, userData: any): Promise<void>;
    warmDashboardCache(dashboardId: string, dashboardData: any): Promise<void>;
    getCacheStats(): Promise<any>;
    getCacheHitRate(): Promise<number>;
    healthCheck(): Promise<{
        status: 'healthy' | 'unhealthy';
        details: any;
    }>;
    private buildKey;
    private createHash;
    private parseRedisInfo;
    createCacheMiddleware(keyGenerator: (req: any) => string, ttl?: number, condition?: (req: any) => boolean): (req: any, res: any, next: any) => Promise<any>;
}
export declare const extendedCacheService: ExtendedCacheService;
export { extendedCacheService as CacheService };
//# sourceMappingURL=cacheService.d.ts.map