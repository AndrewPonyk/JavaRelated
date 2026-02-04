import { prisma } from '@/config/database.js';
import { logger } from '@/utils/logger.js';
import { config } from '@/config/environment.js';
export class DatabaseOptimizer {
    static instance;
    queryMetrics = [];
    slowQueries = new Map();
    config = {
        enableQueryLogging: config.app.env === 'development',
        slowQueryThreshold: 1000,
        batchSize: 100,
        connectionPoolSize: config.database.poolSize,
        queryTimeout: config.performance.databaseQueryTimeout,
        enableQueryOptimization: true,
    };
    constructor() {
        this.setupPerformanceMonitoring();
    }
    static getInstance() {
        if (!DatabaseOptimizer.instance) {
            DatabaseOptimizer.instance = new DatabaseOptimizer();
        }
        return DatabaseOptimizer.instance;
    }
    setupPerformanceMonitoring() {
        setInterval(() => {
            this.cleanupMetrics();
        }, 60 * 60 * 1000);
    }
    async paginateWithCursor(model, options) {
        const startTime = Date.now();
        try {
            const { cursor, take, where, orderBy, include, select } = options;
            const cursorWhere = cursor ? {
                ...where,
                id: { gt: cursor }
            } : where;
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
        }
        catch (error) {
            logger.error('Cursor pagination failed', { error });
            throw error;
        }
    }
    async batchUpsert(model, records, options) {
        const startTime = Date.now();
        const { uniqueField, batchSize = this.config.batchSize, skipDuplicates = false } = options;
        try {
            const results = [];
            for (let i = 0; i < records.length; i += batchSize) {
                const batch = records.slice(i, i + batchSize);
                const batchResults = await prisma.$transaction(batch.map((record) => {
                    const { [uniqueField]: uniqueValue, ...data } = record;
                    return model.upsert({
                        where: { [uniqueField]: uniqueValue },
                        update: data,
                        create: record,
                        ...(skipDuplicates && {
                            onConflict: { ignore: true }
                        }),
                    });
                }), {
                    maxWait: 5000,
                    timeout: 30000,
                });
                results.push(...batchResults);
            }
            this.recordQueryMetrics('BATCH_UPSERT', Date.now() - startTime, results.length);
            logger.info('Batch upsert completed', {
                records: records.length,
                batches: Math.ceil(records.length / batchSize),
                duration: Date.now() - startTime
            });
            return results;
        }
        catch (error) {
            logger.error('Batch upsert failed', { error, recordCount: records.length });
            throw error;
        }
    }
    async performAggregation(query, params = []) {
        const startTime = Date.now();
        try {
            const optimizedQuery = this.addQueryHints(query);
            const result = await prisma.$queryRawUnsafe(optimizedQuery, ...params);
            this.recordQueryMetrics('AGGREGATION', Date.now() - startTime, Array.isArray(result) ? result.length : 1);
            return Array.isArray(result) ? result : [result];
        }
        catch (error) {
            logger.error('Aggregation query failed', { error, query });
            throw error;
        }
    }
    async performFullTextSearch(model, searchQuery, options) {
        const startTime = Date.now();
        const { fields, limit = 20, offset = 0, where = {}, include } = options;
        try {
            const searchConditions = fields.map(field => ({
                [field]: {
                    contains: searchQuery,
                    mode: 'insensitive'
                }
            }));
            const searchWhere = {
                ...where,
                OR: searchConditions
            };
            const [data, total] = await Promise.all([
                model.findMany({
                    where: searchWhere,
                    skip: offset,
                    take: limit,
                    include,
                    orderBy: {
                        updatedAt: 'desc',
                    }
                }),
                model.count({ where: searchWhere })
            ]);
            this.recordQueryMetrics('FULLTEXT_SEARCH', Date.now() - startTime, data.length);
            return { data, total };
        }
        catch (error) {
            logger.error('Full-text search failed', { error, searchQuery });
            throw error;
        }
    }
    async optimizeConnectionPool() {
        try {
            const stats = await this.getConnectionStats();
            logger.info('Database connection pool stats', stats);
            if (stats.activeConnections > stats.poolSize * 0.8) {
                logger.warn('Connection pool utilization high', {
                    utilization: (stats.activeConnections / stats.poolSize) * 100
                });
            }
        }
        catch (error) {
            logger.error('Failed to optimize connection pool', { error });
        }
    }
    getQueryPerformanceReport() {
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
            slowQueries: slowQueryData.slice(0, 10),
            recentMetrics: this.queryMetrics.slice(-20)
        };
    }
    async performHealthCheck() {
        const startTime = Date.now();
        const recommendations = [];
        try {
            const connectionStart = Date.now();
            await prisma.$queryRaw `SELECT 1`;
            const connectionTime = Date.now() - connectionStart;
            const queryStart = Date.now();
            await prisma.$queryRaw `SELECT COUNT(*) FROM "User"`;
            const queryTime = Date.now() - queryStart;
            const connectionStats = await this.getConnectionStats();
            const poolUtilization = connectionStats.activeConnections / connectionStats.poolSize;
            let status = 'healthy';
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
        }
        catch (error) {
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
    cleanupMetrics() {
        const oneHourAgo = new Date(Date.now() - 60 * 60 * 1000);
        this.queryMetrics = this.queryMetrics.filter(m => m.timestamp > oneHourAgo);
        if (this.slowQueries.size > 100) {
            const sortedEntries = Array.from(this.slowQueries.entries())
                .sort(([, a], [, b]) => b - a)
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
    recordQueryMetrics(query, duration, rows, cached = false) {
        if (!this.config.enableQueryLogging)
            return;
        this.queryMetrics.push({
            query,
            duration,
            rows,
            cached,
            timestamp: new Date(),
        });
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
        if (this.queryMetrics.length > 1000) {
            this.queryMetrics = this.queryMetrics.slice(-500);
        }
    }
    addQueryHints(query) {
        if (!this.config.enableQueryOptimization)
            return query;
        if (query.toLowerCase().includes('select')) {
            if (query.toLowerCase().includes('join')) {
                return `SET enable_hashjoin = ON; SET enable_mergejoin = ON; ${query}`;
            }
            if (query.toLowerCase().includes('group by') || query.toLowerCase().includes('count(')) {
                return `SET work_mem = '256MB'; ${query}`;
            }
        }
        return query;
    }
    async getConnectionStats() {
        try {
            return {
                activeConnections: 5,
                poolSize: this.config.connectionPoolSize,
                idleConnections: this.config.connectionPoolSize - 5,
                waitingClients: 0,
            };
        }
        catch (error) {
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
export const databaseOptimizer = DatabaseOptimizer.getInstance();
export const optimizeQuery = async (operation, queryName) => {
    const startTime = Date.now();
    try {
        const result = await operation();
        databaseOptimizer['recordQueryMetrics'](queryName, Date.now() - startTime, 1);
        return result;
    }
    catch (error) {
        databaseOptimizer['recordQueryMetrics'](queryName, Date.now() - startTime, 0);
        throw error;
    }
};
export const QueryBuilders = {
    buildFullTextWhere: (fields, searchTerm, additionalWhere = {}) => ({
        ...additionalWhere,
        OR: fields.map(field => ({
            [field]: {
                contains: searchTerm,
                mode: 'insensitive'
            }
        }))
    }),
    buildPaginationQuery: (page, limit) => ({
        skip: (page - 1) * limit,
        take: limit,
    }),
    buildOptimizedInclude: (relations) => {
        const include = {};
        relations.forEach(relation => {
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
            }
            else if (relation === 'widgets') {
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
            }
            else {
                include[relation] = true;
            }
        });
        return include;
    },
};
//# sourceMappingURL=databaseOptimizer.js.map