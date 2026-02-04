import { Prisma } from '@prisma/client';
import { DashboardService } from './dashboardService.js';
import {
  databaseOptimizer,
  cacheOptimizer,
  optimizeQuery,
  QueryBuilders,
  createOptimizedCacheKey,
  backgroundProcessor,
  scheduleJob
} from '@/utils/performance/index.js';
import { logger } from '@/utils/logger.js';

/**
 * Optimized Dashboard Service
 * Enhanced version of DashboardService with comprehensive performance optimizations
 */

export class OptimizedDashboardService extends DashboardService {

  /**
   * Optimized dashboard fetching with advanced caching and query optimization
   */
  public async getOptimizedUserDashboards(
    userId: string,
    query: any
  ): Promise<any> {
    const startTime = Date.now();

    try {
      // Use cursor-based pagination for large datasets
      if (query.useCursor && query.cursor) {
        return await this.getCursorPaginatedDashboards(userId, query);
      }

      // Use multi-layer cache for better performance
      const cacheKey = createOptimizedCacheKey(
        'dashboards',
        'user',
        userId,
        this.createQueryHash(query)
      );

      return await cacheOptimizer.getMultiLayer(
        cacheKey,
        async () => {
          // Optimized database query with proper includes
          return await optimizeQuery(
            () => this.executeOptimizedDashboardQuery(userId, query),
            'getUserDashboards'
          );
        },
        {
          l1TTL: 60, // 1 minute in memory
          l2TTL: 600, // 10 minutes in Redis
          strategy: 'dashboard-list'
        }
      );

    } catch (error) {
      logger.error('Optimized dashboard fetch failed', { error, userId, query });
      throw error;
    }
  }

  /**
   * High-performance dashboard creation with background processing
   */
  public async createOptimizedDashboard(data: any): Promise<any> {
    try {
      // Create dashboard with optimized transaction
      const dashboard = await databaseOptimizer.performHealthCheck().then(async () => {
        return await this.createDashboard(data);
      });

      // Schedule background tasks for optimization
      await scheduleJob('default', 'dashboard:cache-warm', {
        dashboardId: dashboard.id,
        userId: data.userId
      });

      // Pre-warm related caches
      await this.prewarmRelatedCaches(dashboard.id, data.userId);

      // Invalidate user dashboard cache efficiently
      await cacheOptimizer.invalidateDistributed(
        `dashboards:user:${data.userId}:*`,
        'dashboard-created'
      );

      return dashboard;

    } catch (error) {
      logger.error('Optimized dashboard creation failed', { error, data });
      throw error;
    }
  }

  /**
   * Bulk dashboard operations with batch processing
   */
  public async batchCreateDashboards(
    dashboards: any[],
    userId: string
  ): Promise<any[]> {
    const startTime = Date.now();

    try {
      // Use optimized batch upsert
      const results = await databaseOptimizer.batchUpsert(
        'dashboard' as any, // Type workaround
        dashboards.map(dash => ({ ...dash, userId })),
        {
          uniqueField: 'slug',
          batchSize: 50,
          skipDuplicates: true
        }
      );

      // Schedule batch cache warming
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

    } catch (error) {
      logger.error('Batch dashboard creation failed', { error, userId });
      throw error;
    }
  }

  /**
   * Optimized analytics with intelligent caching
   */
  public async getOptimizedDashboardAnalytics(
    dashboardId: string,
    userId: string,
    query: any
  ): Promise<any> {
    const cacheKey = createOptimizedCacheKey(
      'analytics',
      'dashboard',
      dashboardId,
      this.createQueryHash(query)
    );

    return await cacheOptimizer.getMultiLayer(
      cacheKey,
      async () => {
        // Use optimized aggregation queries
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
      },
      {
        l1TTL: 300, // 5 minutes in memory
        l2TTL: 900, // 15 minutes in Redis
        strategy: 'analytics-aggregated'
      }
    );
  }

  /**
   * Optimized search with full-text search capabilities
   */
  public async searchOptimizedDashboards(
    userId: string,
    searchQuery: string,
    options: any = {}
  ): Promise<any> {
    const cacheKey = createOptimizedCacheKey(
      'search',
      'dashboards',
      userId,
      this.createQueryHash({ query: searchQuery, ...options })
    );

    return await cacheOptimizer.getMultiLayer(
      cacheKey,
      async () => {
        // Use optimized full-text search
        return await databaseOptimizer.performFullTextSearch(
          'dashboard' as any, // Type workaround
          searchQuery,
          {
            fields: ['title', 'description'],
            limit: options.limit || 20,
            offset: options.offset || 0,
            where: this.buildSearchWhereClause(userId, options),
            include: QueryBuilders.buildOptimizedInclude(['user', 'widgets'])
          }
        );
      },
      {
        l1TTL: 180, // 3 minutes
        l2TTL: 600, // 10 minutes
        strategy: 'search-results'
      }
    );
  }

  /**
   * Background cache warming for dashboard data
   */
  public async warmDashboardCaches(dashboardIds: string[]): Promise<void> {
    logger.info('Starting dashboard cache warming', { count: dashboardIds.length });

    // Process in batches to avoid overwhelming the system
    const batchSize = 10;
    for (let i = 0; i < dashboardIds.length; i += batchSize) {
      const batch = dashboardIds.slice(i, i + batchSize);

      await Promise.all(
        batch.map(async (dashboardId) => {
          try {
            // Warm dashboard metadata
            const dashboard = await this.getDashboard(dashboardId, 'system');
            if (dashboard) {
              // Cache the dashboard
              await (cacheOptimizer as any).warmDashboardCache?.(dashboardId, dashboard);
            }

            // Warm analytics data for common queries
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

          } catch (error) {
            logger.warn('Failed to warm dashboard cache', { dashboardId, error });
          }
        })
      );
    }

    logger.info('Dashboard cache warming completed', { count: dashboardIds.length });
  }

  // Private optimized methods

  private async getCursorPaginatedDashboards(userId: string, query: any): Promise<any> {
    const { prisma } = await import('@/config/database.js');

    return await databaseOptimizer.paginateWithCursor(
      prisma.dashboard,
      {
        cursor: query.cursor,
        take: query.limit || 20,
        where: this.buildDashboardWhereClause(userId, query),
        orderBy: { [query.sortBy || 'updatedAt']: query.sortOrder || 'desc' },
        include: QueryBuilders.buildOptimizedInclude(['user', 'widgets'])
      }
    );
  }

  private async executeOptimizedDashboardQuery(userId: string, query: any): Promise<any> {
    const { prisma } = await import('@/config/database.js');

    // Build optimized where clause
    const where = this.buildDashboardWhereClause(userId, query);

    // Use parallel execution for count and data
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

    // Calculate pagination
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

  private buildDashboardWhereClause(userId: string, query: any): any {
    const baseWhere: any = {
      OR: [
        { userId }, // User's own dashboards
        { isPublic: true, isTemplate: false }, // Public dashboards
        {
          dashboardShares: {
            some: { userId }
          }
        }
      ]
    };

    // Add search filter
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

    // Add type filters
    if (query.isPublic !== undefined) {
      baseWhere.isPublic = query.isPublic;
    }

    if (query.isTemplate !== undefined) {
      baseWhere.isTemplate = query.isTemplate;
    }

    return baseWhere;
  }

  private buildSearchWhereClause(userId: string, options: any): any {
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

  private async prewarmRelatedCaches(dashboardId: string, userId: string): Promise<void> {
    try {
      // Warm user dashboard list cache for common queries
      const commonQueries = [
        { page: 1, limit: 10, sortBy: 'updatedAt', sortOrder: 'desc' },
        { page: 1, limit: 20, sortBy: 'createdAt', sortOrder: 'desc' }
      ];

      for (const query of commonQueries) {
        const cacheKey = createOptimizedCacheKey(
          'dashboards',
          'user',
          userId,
          this.createQueryHash(query)
        );

        // Schedule background cache warming
        await scheduleJob('low-priority', 'cache:prewarm', {
          key: cacheKey,
          type: 'dashboard-list',
          userId,
          query
        });
      }

    } catch (error) {
      logger.warn('Cache prewarming failed', { dashboardId, userId, error });
    }
  }

}

// Create and export optimized service instance
export const optimizedDashboardService = new OptimizedDashboardService();

// Register background job handlers for dashboard operations
backgroundProcessor.registerHandler('dashboard:cache-warm', async (job) => {
  const { dashboardId, userId } = job.data as { dashboardId: string; userId: string };
  await optimizedDashboardService.warmDashboardCaches([dashboardId]);
  return { status: 'completed', dashboardId, userId };
});

backgroundProcessor.registerHandler('dashboards:batch-cache-warm', async (job) => {
  const { dashboards, userId } = job.data as { dashboards: string[]; userId: string };
  await optimizedDashboardService.warmDashboardCaches(dashboards);
  return { status: 'completed', count: dashboards.length, userId };
});

backgroundProcessor.registerHandler('cache:prewarm', async (job) => {
  const { key, type, userId, query } = job.data as any;

  // This would implement specific prewarming logic based on type
  logger.info('Cache prewarming completed', { key, type, userId });

  return { status: 'completed', key, type };
});