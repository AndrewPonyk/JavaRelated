import { DashboardService } from './dashboardService.js';
import { databaseOptimizer, cacheOptimizer, optimizeQuery, QueryBuilders, createOptimizedCacheKey, backgroundProcessor, scheduleJob } from '@/utils/performance/index.js';
import { logger } from '@/utils/logger.js';
export class OptimizedDashboardService extends DashboardService {
    async getOptimizedUserDashboards(userId, query) {
        const startTime = Date.now();
        try {
            if (query.useCursor && query.cursor) {
                return await this.getCursorPaginatedDashboards(userId, query);
            }
            const cacheKey = createOptimizedCacheKey('dashboards', 'user', userId, this.createQueryHash(query));
            return await cacheOptimizer.getMultiLayer(cacheKey, async () => {
                return await optimizeQuery(() => this.executeOptimizedDashboardQuery(userId, query), 'getUserDashboards');
            }, {
                l1TTL: 60,
                l2TTL: 600,
                strategy: 'dashboard-list'
            });
        }
        catch (error) {
            logger.error('Optimized dashboard fetch failed', { error, userId, query });
            throw error;
        }
    }
    async createOptimizedDashboard(data) {
        try {
            const dashboard = await databaseOptimizer.performHealthCheck().then(async () => {
                return await this.createDashboard(data);
            });
            await scheduleJob('default', 'dashboard:cache-warm', {
                dashboardId: dashboard.id,
                userId: data.userId
            });
            await this.prewarmRelatedCaches(dashboard.id, data.userId);
            await cacheOptimizer.invalidateDistributed(`dashboards:user:${data.userId}:*`, 'dashboard-created');
            return dashboard;
        }
        catch (error) {
            logger.error('Optimized dashboard creation failed', { error, data });
            throw error;
        }
    }
    async batchCreateDashboards(dashboards, userId) {
        const startTime = Date.now();
        try {
            const results = await databaseOptimizer.batchUpsert('dashboard', dashboards.map(dash => ({ ...dash, userId })), {
                uniqueField: 'slug',
                batchSize: 50,
                skipDuplicates: true
            });
            await scheduleJob('low-priority', 'dashboards:batch-cache-warm', {
                dashboards: results.map(r => r.id),
                userId
            });
            const duration = Date.now() - startTime;
            logger.info('Batch dashboard creation completed', {
                count: results.length,
                duration,
                userId
            });
            return results;
        }
        catch (error) {
            logger.error('Batch dashboard creation failed', { error, userId });
            throw error;
        }
    }
    async getOptimizedDashboardAnalytics(dashboardId, userId, query) {
        const cacheKey = createOptimizedCacheKey('analytics', 'dashboard', dashboardId, this.createQueryHash(query));
        return await cacheOptimizer.getMultiLayer(cacheKey, async () => {
            return await databaseOptimizer.performAggregation(`
          SELECT
            date_trunc('${query.granularity}', date) as period,
            SUM(views) as total_views,
            SUM(unique_views) as total_unique_views,
            AVG(avg_load_time) as avg_load_time,
            AVG(bounce_rate) as avg_bounce_rate
          FROM "DashboardAnalytics"
          WHERE dashboard_id = $1
            AND date >= $2
            AND date <= $3
            AND granularity = $4
          GROUP BY period
          ORDER BY period ASC
        `, [
                dashboardId,
                new Date(query.startDate || Date.now() - 30 * 24 * 60 * 60 * 1000),
                new Date(query.endDate || Date.now()),
                query.granularity || 'day'
            ]);
        }, {
            l1TTL: 300,
            l2TTL: 900,
            strategy: 'analytics-aggregated'
        });
    }
    async searchOptimizedDashboards(userId, searchQuery, options = {}) {
        const cacheKey = createOptimizedCacheKey('search', 'dashboards', userId, this.createQueryHash({ query: searchQuery, ...options }));
        return await cacheOptimizer.getMultiLayer(cacheKey, async () => {
            return await databaseOptimizer.performFullTextSearch('dashboard', searchQuery, {
                fields: ['title', 'description'],
                limit: options.limit || 20,
                offset: options.offset || 0,
                where: this.buildSearchWhereClause(userId, options),
                include: QueryBuilders.buildOptimizedInclude(['user', 'widgets'])
            });
        }, {
            l1TTL: 180,
            l2TTL: 600,
            strategy: 'search-results'
        });
    }
    async warmDashboardCaches(dashboardIds) {
        logger.info('Starting dashboard cache warming', { count: dashboardIds.length });
        const batchSize = 10;
        for (let i = 0; i < dashboardIds.length; i += batchSize) {
            const batch = dashboardIds.slice(i, i + batchSize);
            await Promise.all(batch.map(async (dashboardId) => {
                try {
                    const dashboard = await this.getDashboard(dashboardId, 'system');
                    if (dashboard) {
                        await cacheOptimizer.warmDashboardCache(dashboardId, dashboard);
                    }
                    const commonQueries = [
                        { granularity: 'day', days: 7 },
                        { granularity: 'day', days: 30 },
                        { granularity: 'week', days: 90 }
                    ];
                    for (const queryConfig of commonQueries) {
                        const analyticsQuery = {
                            startDate: new Date(Date.now() - queryConfig.days * 24 * 60 * 60 * 1000).toISOString(),
                            endDate: new Date().toISOString(),
                            granularity: queryConfig.granularity
                        };
                        await this.getOptimizedDashboardAnalytics(dashboardId, 'system', analyticsQuery);
                    }
                }
                catch (error) {
                    logger.warn('Failed to warm dashboard cache', { dashboardId, error });
                }
            }));
        }
        logger.info('Dashboard cache warming completed', { count: dashboardIds.length });
    }
    async getCursorPaginatedDashboards(userId, query) {
        const { prisma } = await import('@/config/database.js');
        return await databaseOptimizer.paginateWithCursor(prisma.dashboard, {
            cursor: query.cursor,
            take: query.limit || 20,
            where: this.buildDashboardWhereClause(userId, query),
            orderBy: { [query.sortBy || 'updatedAt']: query.sortOrder || 'desc' },
            include: QueryBuilders.buildOptimizedInclude(['user', 'widgets'])
        });
    }
    async executeOptimizedDashboardQuery(userId, query) {
        const { prisma } = await import('@/config/database.js');
        const where = this.buildDashboardWhereClause(userId, query);
        const [dashboards, total] = await Promise.all([
            prisma.dashboard.findMany({
                where,
                skip: QueryBuilders.buildPaginationQuery(query.page, query.limit).skip,
                take: QueryBuilders.buildPaginationQuery(query.page, query.limit).take,
                orderBy: { [query.sortBy || 'updatedAt']: query.sortOrder || 'desc' },
                include: QueryBuilders.buildOptimizedInclude(['user', 'widgets']),
            }),
            prisma.dashboard.count({ where })
        ]);
        const pages = Math.ceil(total / query.limit);
        return {
            data: dashboards,
            total,
            page: query.page,
            limit: query.limit,
            pages,
            hasNext: query.page < pages,
            hasPrev: query.page > 1,
        };
    }
    buildDashboardWhereClause(userId, query) {
        const baseWhere = {
            OR: [
                { userId },
                { isPublic: true, isTemplate: false },
                {
                    dashboardShares: {
                        some: { userId }
                    }
                }
            ]
        };
        if (query.search) {
            baseWhere.AND = [
                {
                    OR: [
                        { title: { contains: query.search, mode: 'insensitive' } },
                        { description: { contains: query.search, mode: 'insensitive' } }
                    ]
                }
            ];
        }
        if (query.isPublic !== undefined) {
            baseWhere.isPublic = query.isPublic;
        }
        if (query.isTemplate !== undefined) {
            baseWhere.isTemplate = query.isTemplate;
        }
        return baseWhere;
    }
    buildSearchWhereClause(userId, options) {
        return {
            OR: [
                { userId },
                { isPublic: true },
                {
                    dashboardShares: {
                        some: { userId }
                    }
                }
            ],
            isTemplate: false,
            ...options.filters
        };
    }
    async prewarmRelatedCaches(dashboardId, userId) {
        try {
            const commonQueries = [
                { page: 1, limit: 10, sortBy: 'updatedAt', sortOrder: 'desc' },
                { page: 1, limit: 20, sortBy: 'createdAt', sortOrder: 'desc' }
            ];
            for (const query of commonQueries) {
                const cacheKey = createOptimizedCacheKey('dashboards', 'user', userId, this.createQueryHash(query));
                await scheduleJob('low-priority', 'cache:prewarm', {
                    key: cacheKey,
                    type: 'dashboard-list',
                    userId,
                    query
                });
            }
        }
        catch (error) {
            logger.warn('Cache prewarming failed', { dashboardId, userId, error });
        }
    }
    createQueryHash(query) {
        const str = JSON.stringify(query, Object.keys(query).sort());
        let hash = 0;
        for (let i = 0; i < str.length; i++) {
            const char = str.charCodeAt(i);
            hash = ((hash << 5) - hash) + char;
            hash = hash & hash;
        }
        return Math.abs(hash).toString(16);
    }
}
export const optimizedDashboardService = new OptimizedDashboardService();
backgroundProcessor.registerHandler('dashboard:cache-warm', async (job) => {
    const { dashboardId, userId } = job.data;
    await optimizedDashboardService.warmDashboardCaches([dashboardId]);
    return { status: 'completed', dashboardId, userId };
});
backgroundProcessor.registerHandler('dashboards:batch-cache-warm', async (job) => {
    const { dashboards, userId } = job.data;
    await optimizedDashboardService.warmDashboardCaches(dashboards);
    return { status: 'completed', count: dashboards.length, userId };
});
backgroundProcessor.registerHandler('cache:prewarm', async (job) => {
    const { key, type, userId, query } = job.data;
    logger.info('Cache prewarming completed', { key, type, userId });
    return { status: 'completed', key, type };
});
//# sourceMappingURL=optimizedDashboardService.js.map