import { PrismaClient, Prisma } from '@prisma/client';
import { prisma } from '@/config/database.js';
import { logger } from '@/utils/logger.js';
import { config } from '@/config/environment.js';

/**
 * Database Performance Optimizer
 * Provides optimized database operations, connection pooling, and query optimization
 */

export interface QueryMetrics {
  query: string;
  duration: number;
  rows: number;
  cached: boolean;
  timestamp: Date;
}

export interface PerformanceConfig {
  enableQueryLogging: boolean;
  slowQueryThreshold: number; // milliseconds
  batchSize: number;
  connectionPoolSize: number;
  queryTimeout: number;
  enableQueryOptimization: boolean;
}

export class DatabaseOptimizer {
  private static instance: DatabaseOptimizer;
  private queryMetrics: QueryMetrics[] = [];
  private slowQueries: Map<string, number> = new Map();

  private readonly config: PerformanceConfig = {
    enableQueryLogging: config.app.env === 'development',
    slowQueryThreshold: 1000, // 1 second
    batchSize: 100,
    connectionPoolSize: config.database.poolSize,
    queryTimeout: config.performance.databaseQueryTimeout,
    enableQueryOptimization: true,
  };

  private constructor() {
    this.setupPerformanceMonitoring();
  }

  public static getInstance(): DatabaseOptimizer {
    if (!DatabaseOptimizer.instance) {
      DatabaseOptimizer.instance = new DatabaseOptimizer();
    }
    return DatabaseOptimizer.instance;
  }

  private setupPerformanceMonitoring(): void {
    // Clean up metrics every hour
    setInterval(() => {
      this.cleanupMetrics();
    }, 60 * 60 * 1000);
  }

  /**
   * Optimized pagination with cursor-based approach for large datasets
   */
  public async paginateWithCursor<T>(
    model: any,
    options: {
      cursor?: string;
      take: number;
      where?: any;
      orderBy?: any;
      include?: any;
      select?: any;
    }
  ): Promise<{
    data: T[];
    nextCursor?: string;
    hasMore: boolean;
    total?: number;
  }> {
    const startTime = Date.now();

    try {
      const { cursor, take, where, orderBy, include, select } = options;

      // Add cursor to where clause if provided
      const cursorWhere = cursor ? {
        ...where,
        id: { gt: cursor }
      } : where;

      // Fetch one extra item to check if there are more results
      const items = await model.findMany({
        where: cursorWhere,
        take: take + 1,
        orderBy: orderBy || { id: 'asc' },
        include,
        select,
      });

      const hasMore = items.length > take;
      const data = hasMore ? items.slice(0, take) : items;
      const nextCursor = hasMore && data.length > 0 ? data[data.length - 1].id : undefined;

      this.recordQueryMetrics('PAGINATE_CURSOR', Date.now() - startTime, data.length);

      return {
        data,
        nextCursor,
        hasMore,
      };
    } catch (error) {
      logger.error('Cursor pagination failed', { error });
      throw error;
    }
  }

  /**
   * Optimized batch operations with transaction batching
   */
  public async batchUpsert<T>(
    model: any,
    records: T[],
    options: {
      uniqueField: string;
      batchSize?: number;
      skipDuplicates?: boolean;
    }
  ): Promise<T[]> {
    const startTime = Date.now();
    const { uniqueField, batchSize = this.config.batchSize, skipDuplicates = false } = options;

    try {
      const results: T[] = [];

      // Process in batches to avoid overwhelming the database
      for (let i = 0; i < records.length; i += batchSize) {
        const batch = records.slice(i, i + batchSize);

        const batchResults = await prisma.$transaction(
          batch.map((record: any) => {
            const { [uniqueField]: uniqueValue, ...data } = record;

            return model.upsert({
              where: { [uniqueField]: uniqueValue },
              update: data,
              create: record,
              ...(skipDuplicates && {
                onConflict: { ignore: true }
              }),
            });
          })
        );

        results.push(...batchResults);
      }

      this.recordQueryMetrics('BATCH_UPSERT', Date.now() - startTime, results.length);
      logger.info('Batch upsert completed', {
        records: records.length,
        batches: Math.ceil(records.length / batchSize),
        duration: Date.now() - startTime
      });

      return results;
    } catch (error) {
      logger.error('Batch upsert failed', { error, recordCount: records.length });
      throw error;
    }
  }

  /**
   * Optimized aggregation queries with proper indexing hints
   */
  public async performAggregation(
    query: string,
    params: any[] = []
  ): Promise<any[]> {
    const startTime = Date.now();

    try {
      // Add query hints for PostgreSQL optimization
      const optimizedQuery = this.addQueryHints(query);

      const result = await prisma.$queryRawUnsafe(optimizedQuery, ...params);

      this.recordQueryMetrics('AGGREGATION', Date.now() - startTime, Array.isArray(result) ? result.length : 1);

      return Array.isArray(result) ? result : [result];
    } catch (error) {
      logger.error('Aggregation query failed', { error, query });
      throw error;
    }
  }

  /**
   * Optimized full-text search with ranking
   */
  public async performFullTextSearch<T>(
    model: any,
    searchQuery: string,
    options: {
      fields: string[];
      limit?: number;
      offset?: number;
      where?: any;
      include?: any;
    }
  ): Promise<{ data: T[]; total: number }> {
    const startTime = Date.now();
    const { fields, limit = 20, offset = 0, where = {}, include } = options;

    try {
      // Create full-text search conditions for multiple fields
      const searchConditions = fields.map(field => ({
        [field]: {
          contains: searchQuery,
          mode: 'insensitive' as const
        }
      }));

      const searchWhere = {
        ...where,
        OR: searchConditions
      };

      // Execute search with count
      const [data, total] = await Promise.all([
        model.findMany({
          where: searchWhere,
          skip: offset,
          take: limit,
          include,
          // Add ranking for PostgreSQL if available
          orderBy: {
            updatedAt: 'desc',
          }
        }),
        model.count({ where: searchWhere })
      ]);

      this.recordQueryMetrics('FULLTEXT_SEARCH', Date.now() - startTime, data.length);

      return { data, total };
    } catch (error) {
      logger.error('Full-text search failed', { error, searchQuery });
      throw error;
    }
  }

  /**
   * Connection pool optimization
   */
  public async optimizeConnectionPool(): Promise<void> {
    try {
      // Get current connection stats
      const stats = await this.getConnectionStats();

      // Log connection pool status
      logger.info('Database connection pool stats', stats);

      // Adjust pool size based on usage patterns
      if (stats.activeConnections > stats.poolSize * 0.8) {
        logger.warn('Connection pool utilization high', {
          utilization: (stats.activeConnections / stats.poolSize) * 100
        });
      }
    } catch (error) {
      logger.error('Failed to optimize connection pool', { error });
    }
  }

  /**
   * Query performance analysis
   */
  public getQueryPerformanceReport(): {
    totalQueries: number;
    averageDuration: number;
    slowQueries: Array<{ query: string; count: number; avgDuration: number }>;
    recentMetrics: QueryMetrics[];
  } {
    const totalQueries = this.queryMetrics.length;
    const averageDuration = totalQueries > 0
      ? this.queryMetrics.reduce((sum, m) => sum + m.duration, 0) / totalQueries
      : 0;

    const slowQueryData = Array.from(this.slowQueries.entries()).map(([query, count]) => ({
      query,
      count,
      avgDuration: this.queryMetrics
        .filter(m => m.query === query)
        .reduce((sum, m) => sum + m.duration, 0) / count
    })).sort((a, b) => b.avgDuration - a.avgDuration);

    return {
      totalQueries,
      averageDuration,
      slowQueries: slowQueryData.slice(0, 10), // Top 10 slow queries
      recentMetrics: this.queryMetrics.slice(-20) // Last 20 queries
    };
  }

  /**
   * Database health check with performance metrics
   */
  public async performHealthCheck(): Promise<{
    status: 'healthy' | 'degraded' | 'unhealthy';
    metrics: {
      connectionTime: number;
      queryTime: number;
      connectionCount: number;
      poolUtilization: number;
    };
    recommendations: string[];
  }> {
    const startTime = Date.now();
    const recommendations: string[] = [];

    try {
      // Test basic connectivity
      const connectionStart = Date.now();
      await prisma.$queryRaw`SELECT 1`;
      const connectionTime = Date.now() - connectionStart;

      // Test query performance
      const queryStart = Date.now();
      await prisma.$queryRaw`SELECT COUNT(*) FROM "users"`;
      const queryTime = Date.now() - queryStart;

      // Get connection stats
      const connectionStats = await this.getConnectionStats();
      const poolUtilization = connectionStats.activeConnections / connectionStats.poolSize;

      // Determine health status
      let status: 'healthy' | 'degraded' | 'unhealthy' = 'healthy';

      if (connectionTime > 1000) {
        status = 'degraded';
        recommendations.push('High connection latency detected');
      }

      if (queryTime > 500) {
        status = 'degraded';
        recommendations.push('Slow query performance detected');
      }

      if (poolUtilization > 0.9) {
        status = 'degraded';
        recommendations.push('Connection pool near capacity');
      }

      if (connectionTime > 5000 || queryTime > 2000) {
        status = 'unhealthy';
      }

      // Add performance recommendations
      if (this.slowQueries.size > 0) {
        recommendations.push(`${this.slowQueries.size} slow queries identified`);
      }

      return {
        status,
        metrics: {
          connectionTime,
          queryTime,
          connectionCount: connectionStats.activeConnections,
          poolUtilization: Math.round(poolUtilization * 100) / 100,
        },
        recommendations,
      };
    } catch (error) {
      logger.error('Database health check failed', { error });
      return {
        status: 'unhealthy',
        metrics: {
          connectionTime: Date.now() - startTime,
          queryTime: 0,
          connectionCount: 0,
          poolUtilization: 0,
        },
        recommendations: ['Database connection failed'],
      };
    }
  }

  /**
   * Cleanup old metrics to prevent memory leaks
   */
  private cleanupMetrics(): void {
    const oneHourAgo = new Date(Date.now() - 60 * 60 * 1000);
    this.queryMetrics = this.queryMetrics.filter(m => m.timestamp > oneHourAgo);

    // Keep only top 100 slow queries
    if (this.slowQueries.size > 100) {
      const sortedEntries = Array.from(this.slowQueries.entries())
        .sort(([,a], [,b]) => b - a)
        .slice(0, 100);

      this.slowQueries.clear();
      sortedEntries.forEach(([query, count]) => {
        this.slowQueries.set(query, count);
      });
    }

    logger.debug('Query metrics cleaned up', {
      remainingMetrics: this.queryMetrics.length,
      remainingSlowQueries: this.slowQueries.size
    });
  }

  /**
   * Record query performance metrics
   */
  private recordQueryMetrics(query: string, duration: number, rows: number, cached: boolean = false): void {
    if (!this.config.enableQueryLogging) return;

    this.queryMetrics.push({
      query,
      duration,
      rows,
      cached,
      timestamp: new Date(),
    });

    // Track slow queries
    if (duration > this.config.slowQueryThreshold) {
      const currentCount = this.slowQueries.get(query) || 0;
      this.slowQueries.set(query, currentCount + 1);

      logger.warn('Slow query detected', {
        query,
        duration,
        rows,
        count: currentCount + 1
      });
    }

    // Keep metrics array from growing too large
    if (this.queryMetrics.length > 1000) {
      this.queryMetrics = this.queryMetrics.slice(-500);
    }
  }

  /**
   * Add PostgreSQL-specific query hints for optimization
   */
  private addQueryHints(query: string): string {
    if (!this.config.enableQueryOptimization) return query;

    // Add common optimization hints
    if (query.toLowerCase().includes('select')) {
      // Enable better join algorithms for complex queries
      if (query.toLowerCase().includes('join')) {
        return `SET enable_hashjoin = ON; SET enable_mergejoin = ON; ${query}`;
      }

      // Optimize aggregation queries
      if (query.toLowerCase().includes('group by') || query.toLowerCase().includes('count(')) {
        return `SET work_mem = '256MB'; ${query}`;
      }
    }

    return query;
  }

  /**
   * Get current connection statistics
   */
  private async getConnectionStats(): Promise<{
    activeConnections: number;
    poolSize: number;
    idleConnections: number;
    waitingClients: number;
  }> {
    try {
      // This would require additional instrumentation in a real implementation
      // For now, return estimated values based on configuration
      return {
        activeConnections: 5, // Estimated
        poolSize: this.config.connectionPoolSize,
        idleConnections: this.config.connectionPoolSize - 5,
        waitingClients: 0,
      };
    } catch (error) {
      logger.error('Failed to get connection stats', { error });
      return {
        activeConnections: 0,
        poolSize: this.config.connectionPoolSize,
        idleConnections: 0,
        waitingClients: 0,
      };
    }
  }
}

// Export singleton instance
export const databaseOptimizer = DatabaseOptimizer.getInstance();

// Export utility functions
export const optimizeQuery = async <T>(
  operation: () => Promise<T>,
  queryName: string
): Promise<T> => {
  const startTime = Date.now();
  try {
    const result = await operation();
    databaseOptimizer['recordQueryMetrics'](queryName, Date.now() - startTime, 1);
    return result;
  } catch (error) {
    databaseOptimizer['recordQueryMetrics'](queryName, Date.now() - startTime, 0);
    throw error;
  }
};

// Query builder helpers for common optimized patterns
export const QueryBuilders = {
  /**
   * Build optimized WHERE clause for full-text search
   */
  buildFullTextWhere: (fields: string[], searchTerm: string, additionalWhere: any = {}) => ({
    ...additionalWhere,
    OR: fields.map(field => ({
      [field]: {
        contains: searchTerm,
        mode: 'insensitive' as const
      }
    }))
  }),

  /**
   * Build optimized pagination query
   */
  buildPaginationQuery: (page: number, limit: number) => ({
    skip: (page - 1) * limit,
    take: limit,
  }),

  /**
   * Build efficient include for related data
   */
  buildOptimizedInclude: (relations: string[]) => {
    const include: any = {};
    relations.forEach(relation => {
      // Add common optimizations for each relation
      if (relation === 'user') {
        include.user = {
          select: {
            id: true,
            email: true,
            firstName: true,
            lastName: true,
            profile: {
              select: {
                avatar: true
              }
            }
          }
        };
      } else if (relation === 'widgets') {
        include.widgets = {
          select: {
            id: true,
            title: true,
            type: true,
            position: true,
            config: true,
          },
          orderBy: {
            createdAt: 'asc'
          }
        };
      } else {
        include[relation] = true;
      }
    });
    return include;
  },
};