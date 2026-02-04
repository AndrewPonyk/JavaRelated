import { DatabaseOptimizer } from '@/utils/performance/databaseOptimizer.js';
import { mockPrisma, mockDashboard } from '../../setup.js';

describe('DatabaseOptimizer', () => {
  let databaseOptimizer: DatabaseOptimizer;

  beforeEach(() => {
    databaseOptimizer = DatabaseOptimizer.getInstance();
    clearAllMocks();
  });

  describe('singleton pattern', () => {
    it('should return the same instance', () => {
      const instance1 = DatabaseOptimizer.getInstance();
      const instance2 = DatabaseOptimizer.getInstance();

      expect(instance1).toBe(instance2);
    });
  });

  describe('performHealthCheck', () => {
    it('should return true when database is healthy', async () => {
      mockPrisma.$queryRaw.mockResolvedValue([{ result: 1 }]);

      const isHealthy = await databaseOptimizer.performHealthCheck();

      expect(isHealthy).toBe(true);
      expect(mockPrisma.$queryRaw).toHaveBeenCalledWith(
        expect.any(Object) // Prisma.sql template
      );
    });

    it('should return false when database is unhealthy', async () => {
      const dbError = new Error('Connection timeout');
      mockPrisma.$queryRaw.mockRejectedValue(dbError);

      const isHealthy = await databaseOptimizer.performHealthCheck();

      expect(isHealthy).toBe(false);
    });

    it('should track health check performance', async () => {
      mockPrisma.$queryRaw.mockResolvedValue([{ result: 1 }]);

      const startTime = Date.now();
      await databaseOptimizer.performHealthCheck();

      const metrics = (databaseOptimizer as any).metrics;
      expect(metrics.healthChecks).toBeGreaterThan(0);
      expect(metrics.lastHealthCheck).toBeGreaterThanOrEqual(startTime);
    });
  });

  describe('optimizeConnectionPool', () => {
    it('should optimize connection pool settings', async () => {
      const result = await databaseOptimizer.optimizeConnectionPool();

      expect(result).toEqual({
        success: true,
        previousSettings: expect.any(Object),
        newSettings: expect.any(Object),
        optimization: expect.stringContaining('Connection pool optimized')
      });
    });

    it('should handle optimization errors gracefully', async () => {
      // Mock a scenario where optimization might fail
      const optimizerInstance = databaseOptimizer as any;
      optimizerInstance.getCurrentPoolSettings = jest.fn().mockRejectedValue(
        new Error('Pool settings unavailable')
      );

      const result = await databaseOptimizer.optimizeConnectionPool();

      expect(result.success).toBe(false);
      expect(result.error).toContain('Pool settings unavailable');
    });
  });

  describe('paginateWithCursor', () => {
    const mockPrismaModel = {
      findMany: jest.fn(),
      findFirst: jest.fn(),
      count: jest.fn()
    };

    const paginationOptions = {
      cursor: 'cursor_123',
      take: 20,
      where: { userId: 'user_123' },
      orderBy: { updatedAt: 'desc' as const },
      include: { user: true }
    };

    beforeEach(() => {
      mockPrismaModel.findMany.mockClear();
      mockPrismaModel.findFirst.mockClear();
    });

    it('should perform cursor-based pagination', async () => {
      const mockResults = [mockDashboard];
      const nextCursor = 'next_cursor_456';

      mockPrismaModel.findMany.mockResolvedValue([...mockResults, { id: nextCursor }]);
      mockPrismaModel.findFirst.mockResolvedValue({ id: nextCursor });

      const result = await databaseOptimizer.paginateWithCursor(
        mockPrismaModel as any,
        paginationOptions
      );

      expect(result).toEqual({
        data: mockResults,
        nextCursor,
        hasNextPage: true,
        pageInfo: {
          startCursor: mockResults[0].id,
          endCursor: mockResults[mockResults.length - 1].id,
          hasNextPage: true,
          hasPreviousPage: true
        }
      });

      expect(mockPrismaModel.findMany).toHaveBeenCalledWith({
        ...paginationOptions,
        take: paginationOptions.take + 1 // +1 to check for next page
      });
    });

    it('should handle first page correctly', async () => {
      const mockResults = [mockDashboard];
      const firstPageOptions = { ...paginationOptions, cursor: undefined };

      mockPrismaModel.findMany.mockResolvedValue(mockResults);

      const result = await databaseOptimizer.paginateWithCursor(
        mockPrismaModel as any,
        firstPageOptions
      );

      expect(result.pageInfo.hasPreviousPage).toBe(false);
      expect(mockPrismaModel.findMany).toHaveBeenCalledWith({
        ...firstPageOptions,
        take: firstPageOptions.take + 1,
        cursor: undefined
      });
    });

    it('should handle last page correctly', async () => {
      const mockResults = [mockDashboard];

      mockPrismaModel.findMany.mockResolvedValue(mockResults);

      const result = await databaseOptimizer.paginateWithCursor(
        mockPrismaModel as any,
        paginationOptions
      );

      expect(result.hasNextPage).toBe(false);
      expect(result.nextCursor).toBeNull();
    });

    it('should handle empty results', async () => {
      mockPrismaModel.findMany.mockResolvedValue([]);

      const result = await databaseOptimizer.paginateWithCursor(
        mockPrismaModel as any,
        paginationOptions
      );

      expect(result).toEqual({
        data: [],
        nextCursor: null,
        hasNextPage: false,
        pageInfo: {
          startCursor: null,
          endCursor: null,
          hasNextPage: false,
          hasPreviousPage: true // Cursor was provided
        }
      });
    });
  });

  describe('batchUpsert', () => {
    const tableName = 'dashboard';
    const records = [
      { id: '1', title: 'Dashboard 1', slug: 'dashboard-1' },
      { id: '2', title: 'Dashboard 2', slug: 'dashboard-2' },
      { id: '3', title: 'Dashboard 3', slug: 'dashboard-3' }
    ];
    const options = {
      uniqueField: 'slug',
      batchSize: 2,
      skipDuplicates: true
    };

    it('should perform batch upsert operations', async () => {
      const upsertedRecords = records.map(record => ({ ...record, updated: true }));
      mockPrisma.$transaction.mockResolvedValue(upsertedRecords);

      const result = await databaseOptimizer.batchUpsert(tableName, records, options);

      expect(result).toEqual(upsertedRecords);
      expect(mockPrisma.$transaction).toHaveBeenCalled();

      // Should split into batches based on batchSize
      const transactionCall = (mockPrisma.$transaction as jest.Mock).mock.calls[0][0];
      expect(Array.isArray(transactionCall)).toBe(true);
    });

    it('should handle large datasets with proper batching', async () => {
      const largeDataset = Array.from({ length: 150 }, (_, i) => ({
        id: `${i}`,
        title: `Dashboard ${i}`,
        slug: `dashboard-${i}`
      }));

      const batchedResults = largeDataset.map(record => ({ ...record, updated: true }));
      mockPrisma.$transaction.mockResolvedValue(batchedResults);

      const result = await databaseOptimizer.batchUpsert(
        tableName,
        largeDataset,
        { ...options, batchSize: 50 }
      );

      expect(result).toEqual(batchedResults);

      // Should be called multiple times due to batching
      expect(mockPrisma.$transaction).toHaveBeenCalled();
    });

    it('should handle upsert conflicts with skipDuplicates', async () => {
      const conflictError = new Error('Unique constraint violation');
      mockPrisma.$transaction.mockRejectedValueOnce(conflictError);

      const successfulUpsert = records.slice(1); // Skip first conflicting record
      mockPrisma.$transaction.mockResolvedValueOnce(successfulUpsert);

      const result = await databaseOptimizer.batchUpsert(tableName, records, {
        ...options,
        skipDuplicates: true
      });

      expect(result).toEqual(successfulUpsert);
    });

    it('should throw error when skipDuplicates is false', async () => {
      const conflictError = new Error('Unique constraint violation');
      mockPrisma.$transaction.mockRejectedValue(conflictError);

      await expect(
        databaseOptimizer.batchUpsert(tableName, records, {
          ...options,
          skipDuplicates: false
        })
      ).rejects.toThrow('Unique constraint violation');
    });
  });

  describe('performAggregation', () => {
    const aggregationQuery = `
      SELECT
        date_trunc('day', date) as period,
        SUM(views) as total_views,
        AVG(load_time) as avg_load_time
      FROM "DashboardAnalytics"
      WHERE dashboard_id = $1 AND date >= $2
      GROUP BY period
      ORDER BY period ASC
    `;

    const queryParams = ['dashboard_123', new Date('2024-01-01')];

    it('should execute aggregation queries with optimization hints', async () => {
      const aggregationResults = [
        { period: '2024-01-01', total_views: 100, avg_load_time: 1.5 },
        { period: '2024-01-02', total_views: 150, avg_load_time: 1.2 }
      ];

      mockPrisma.$queryRaw.mockResolvedValue(aggregationResults);

      const result = await databaseOptimizer.performAggregation(aggregationQuery, queryParams);

      expect(result).toEqual(aggregationResults);
      expect(mockPrisma.$queryRaw).toHaveBeenCalledWith(
        expect.any(Object), // Prisma.sql template
        ...queryParams
      );
    });

    it('should add query hints for PostgreSQL optimization', async () => {
      mockPrisma.$queryRaw.mockResolvedValue([]);

      await databaseOptimizer.performAggregation(aggregationQuery, queryParams);

      // Should add optimization hints to the query
      const queryCall = (mockPrisma.$queryRaw as jest.Mock).mock.calls[0][0];
      expect(queryCall.sql).toContain('/*+ USE INDEX */');
    });

    it('should handle aggregation errors', async () => {
      const sqlError = new Error('Invalid SQL syntax');
      mockPrisma.$queryRaw.mockRejectedValue(sqlError);

      await expect(
        databaseOptimizer.performAggregation(aggregationQuery, queryParams)
      ).rejects.toThrow('Invalid SQL syntax');
    });

    it('should track query performance metrics', async () => {
      mockPrisma.$queryRaw.mockResolvedValue([]);

      const startTime = Date.now();
      await databaseOptimizer.performAggregation(aggregationQuery, queryParams);

      const metrics = (databaseOptimizer as any).metrics;
      expect(metrics.aggregationQueries).toBeGreaterThan(0);
      expect(metrics.totalQueryTime).toBeGreaterThan(0);
    });
  });

  describe('performFullTextSearch', () => {
    const searchQuery = 'dashboard analytics';
    const searchOptions = {
      fields: ['title', 'description'],
      limit: 20,
      offset: 0,
      where: { userId: 'user_123' },
      include: { user: true }
    };

    beforeEach(() => {
      mockPrisma.$queryRaw.mockClear();
    });

    it('should perform full-text search with PostgreSQL', async () => {
      const searchResults = [mockDashboard];
      mockPrisma.$queryRaw.mockResolvedValue(searchResults);

      const result = await databaseOptimizer.performFullTextSearch(
        'dashboard',
        searchQuery,
        searchOptions
      );

      expect(result).toEqual(searchResults);
      expect(mockPrisma.$queryRaw).toHaveBeenCalledWith(
        expect.any(Object), // Prisma.sql template containing full-text search
        expect.stringContaining('dashboard'),
        expect.stringContaining('analytics')
      );
    });

    it('should build proper full-text search query with ranking', async () => {
      mockPrisma.$queryRaw.mockResolvedValue([]);

      await databaseOptimizer.performFullTextSearch(
        'dashboard',
        searchQuery,
        searchOptions
      );

      const queryCall = (mockPrisma.$queryRaw as jest.Mock).mock.calls[0][0];
      expect(queryCall.sql).toContain('ts_rank');
      expect(queryCall.sql).toContain('to_tsvector');
      expect(queryCall.sql).toContain('to_tsquery');
    });

    it('should handle search with multiple fields', async () => {
      mockPrisma.$queryRaw.mockResolvedValue([]);

      await databaseOptimizer.performFullTextSearch(
        'dashboard',
        'test query',
        {
          ...searchOptions,
          fields: ['title', 'description', 'tags']
        }
      );

      const queryCall = (mockPrisma.$queryRaw as jest.Mock).mock.calls[0][0];
      expect(queryCall.sql).toContain('title');
      expect(queryCall.sql).toContain('description');
      expect(queryCall.sql).toContain('tags');
    });

    it('should apply proper limit and offset', async () => {
      mockPrisma.$queryRaw.mockResolvedValue([]);

      await databaseOptimizer.performFullTextSearch(
        'dashboard',
        searchQuery,
        { ...searchOptions, limit: 10, offset: 20 }
      );

      const queryCall = (mockPrisma.$queryRaw as jest.Mock).mock.calls[0][0];
      expect(queryCall.sql).toContain('LIMIT 10');
      expect(queryCall.sql).toContain('OFFSET 20');
    });

    it('should handle empty search query', async () => {
      mockPrisma.$queryRaw.mockResolvedValue([]);

      const result = await databaseOptimizer.performFullTextSearch(
        'dashboard',
        '',
        searchOptions
      );

      expect(result).toEqual([]);
      expect(mockPrisma.$queryRaw).not.toHaveBeenCalled();
    });

    it('should sanitize search query to prevent SQL injection', async () => {
      const maliciousQuery = "'; DROP TABLE users; --";
      mockPrisma.$queryRaw.mockResolvedValue([]);

      await databaseOptimizer.performFullTextSearch(
        'dashboard',
        maliciousQuery,
        searchOptions
      );

      const queryCall = (mockPrisma.$queryRaw as jest.Mock).mock.calls[0][0];
      // Should not contain the malicious SQL
      expect(queryCall.sql).not.toContain('DROP TABLE');
    });
  });

  describe('queryOptimization', () => {
    it('should detect and optimize slow queries', async () => {
      const slowQuery = jest.fn().mockImplementation(() => {
        return new Promise(resolve => setTimeout(resolve, 1000));
      });

      const startTime = Date.now();
      await databaseOptimizer.optimizeQuery(slowQuery, 'test-query');
      const duration = Date.now() - startTime;

      expect(duration).toBeGreaterThan(500); // Should take some time
      expect(slowQuery).toHaveBeenCalled();

      const metrics = (databaseOptimizer as any).metrics;
      expect(metrics.slowQueries).toBeGreaterThan(0);
    });

    it('should provide query optimization suggestions', async () => {
      const queryAnalysis = await databaseOptimizer.analyzeQuery(`
        SELECT * FROM "Dashboard"
        WHERE user_id = 'user_123'
        ORDER BY created_at DESC
      `);

      expect(queryAnalysis).toEqual({
        hasSelectStar: true,
        missingIndexes: expect.any(Array),
        optimizationSuggestions: expect.arrayContaining([
          expect.stringContaining('SELECT *'),
          expect.stringContaining('index')
        ])
      });
    });

    it('should identify missing indexes', async () => {
      const queryAnalysis = await databaseOptimizer.analyzeQuery(`
        SELECT id, title FROM "Dashboard"
        WHERE user_id = $1 AND created_at > $2
        ORDER BY updated_at DESC
      `);

      expect(queryAnalysis.missingIndexes).toEqual(
        expect.arrayContaining([
          expect.stringContaining('user_id'),
          expect.stringContaining('created_at'),
          expect.stringContaining('updated_at')
        ])
      );
    });
  });

  describe('performance monitoring', () => {
    it('should track connection pool utilization', async () => {
      const poolStats = await databaseOptimizer.getConnectionPoolStats();

      expect(poolStats).toEqual({
        size: expect.any(Number),
        used: expect.any(Number),
        idle: expect.any(Number),
        pending: expect.any(Number),
        utilization: expect.any(Number)
      });
    });

    it('should provide comprehensive performance metrics', async () => {
      const metrics = databaseOptimizer.getPerformanceMetrics();

      expect(metrics).toEqual({
        connectionPool: {
          utilization: expect.any(Number),
          averageWaitTime: expect.any(Number)
        },
        queries: {
          total: expect.any(Number),
          average: expect.any(Number),
          slow: expect.any(Number),
          failed: expect.any(Number)
        },
        optimization: {
          indexHitRatio: expect.any(Number),
          cacheHitRatio: expect.any(Number),
          recommendationsCount: expect.any(Number)
        }
      });
    });

    it('should detect performance degradation', async () => {
      // Simulate multiple slow queries
      for (let i = 0; i < 10; i++) {
        try {
          await databaseOptimizer.optimizeQuery(
            () => new Promise(resolve => setTimeout(resolve, 600)),
            `slow-query-${i}`
          );
        } catch (error) {
          // Expected for some queries
        }
      }

      const healthStatus = await databaseOptimizer.getHealthStatus();

      expect(healthStatus).toEqual({
        status: expect.stringMatching(/healthy|warning|critical/),
        issues: expect.any(Array),
        recommendations: expect.any(Array),
        metrics: expect.any(Object)
      });
    });
  });

  describe('cleanup and maintenance', () => {
    it('should clean up expired metrics', async () => {
      const optimizer = databaseOptimizer as any;

      // Add some old metrics
      optimizer.metrics.queryHistory = [
        { timestamp: Date.now() - 25 * 60 * 60 * 1000, duration: 100 }, // 25 hours old
        { timestamp: Date.now() - 1 * 60 * 60 * 1000, duration: 150 }   // 1 hour old
      ];

      await optimizer.cleanupMetrics();

      // Should keep only recent metrics (within 24 hours)
      expect(optimizer.metrics.queryHistory).toHaveLength(1);
      expect(optimizer.metrics.queryHistory[0].duration).toBe(150);
    });

    it('should reset metrics when requested', () => {
      const optimizer = databaseOptimizer as any;

      // Populate some metrics
      optimizer.metrics.healthChecks = 10;
      optimizer.metrics.totalQueries = 100;
      optimizer.metrics.slowQueries = 5;

      databaseOptimizer.resetMetrics();

      expect(optimizer.metrics.healthChecks).toBe(0);
      expect(optimizer.metrics.totalQueries).toBe(0);
      expect(optimizer.metrics.slowQueries).toBe(0);
    });
  });
});