import { OptimizedDashboardService } from '@/services/dashboard/optimizedDashboardService.js';
import { mockPrisma, mockUser, mockDashboard, mockWidget } from '../../setup.js';

// Mock performance utilities
jest.mock('@/utils/performance/index.js', () => ({
  databaseOptimizer: {
    performHealthCheck: jest.fn().mockResolvedValue(true),
    batchUpsert: jest.fn(),
    paginateWithCursor: jest.fn(),
    performAggregation: jest.fn(),
    performFullTextSearch: jest.fn()
  },
  cacheOptimizer: {
    getMultiLayer: jest.fn(),
    invalidateDistributed: jest.fn(),
    warmDashboardCache: jest.fn()
  },
  optimizeQuery: jest.fn(),
  QueryBuilders: {
    buildOptimizedInclude: jest.fn().mockReturnValue({ user: true, widgets: true }),
    buildPaginationQuery: jest.fn().mockReturnValue({ skip: 0, take: 10 })
  },
  createOptimizedCacheKey: jest.fn(),
  backgroundProcessor: jest.fn(),
  scheduleJob: jest.fn()
}));

describe('OptimizedDashboardService', () => {
  let optimizedDashboardService: OptimizedDashboardService;
  let mockCacheOptimizer: any;
  let mockDatabaseOptimizer: any;
  let mockScheduleJob: any;

  beforeEach(() => {
    optimizedDashboardService = new OptimizedDashboardService();
    clearAllMocks();

    // Get mocked modules
    const performanceUtils = require('@/utils/performance/index.js');
    mockCacheOptimizer = performanceUtils.cacheOptimizer;
    mockDatabaseOptimizer = performanceUtils.databaseOptimizer;
    mockScheduleJob = performanceUtils.scheduleJob;

    // Reset mocks
    mockCacheOptimizer.getMultiLayer.mockClear();
    mockDatabaseOptimizer.batchUpsert.mockClear();
    mockScheduleJob.mockClear();
  });

  describe('getOptimizedUserDashboards', () => {
    const query = {
      page: 1,
      limit: 10,
      sortBy: 'updatedAt',
      sortOrder: 'desc'
    };

    it('should use multi-layer caching for dashboard queries', async () => {
      const mockDashboards = { data: [mockDashboard], total: 1 };
      mockCacheOptimizer.getMultiLayer.mockResolvedValue(mockDashboards);

      const result = await optimizedDashboardService.getOptimizedUserDashboards(
        mockUser.id,
        query
      );

      expect(result).toEqual(mockDashboards);
      expect(mockCacheOptimizer.getMultiLayer).toHaveBeenCalledWith(
        expect.any(String), // cache key
        expect.any(Function), // query function
        {
          l1TTL: 60,
          l2TTL: 600,
          strategy: 'dashboard-list'
        }
      );
    });

    it('should use cursor pagination for large datasets', async () => {
      const cursorQuery = { ...query, useCursor: true, cursor: 'cursor_123' };
      const mockResult = { data: [mockDashboard], hasNextPage: false };

      mockDatabaseOptimizer.paginateWithCursor.mockResolvedValue(mockResult);

      const result = await optimizedDashboardService.getOptimizedUserDashboards(
        mockUser.id,
        cursorQuery
      );

      expect(mockDatabaseOptimizer.paginateWithCursor).toHaveBeenCalledWith(
        expect.any(Object), // prisma.dashboard
        expect.objectContaining({
          cursor: 'cursor_123',
          take: query.limit
        })
      );
    });

    it('should handle cache misses and execute optimized queries', async () => {
      const mockDashboards = { data: [mockDashboard], total: 1 };

      // Mock cache miss - function gets called
      mockCacheOptimizer.getMultiLayer.mockImplementation(
        async (key: string, queryFn: Function) => {
          return await queryFn();
        }
      );

      // Mock the database query result
      mockPrisma.dashboard.findMany.mockResolvedValue([mockDashboard]);
      mockPrisma.dashboard.count.mockResolvedValue(1);

      const result = await optimizedDashboardService.getOptimizedUserDashboards(
        mockUser.id,
        query
      );

      expect(mockCacheOptimizer.getMultiLayer).toHaveBeenCalled();
      expect(mockPrisma.dashboard.findMany).toHaveBeenCalled();
      expect(mockPrisma.dashboard.count).toHaveBeenCalled();
    });

    it('should handle optimization errors gracefully', async () => {
      const error = new Error('Cache optimization failed');
      mockCacheOptimizer.getMultiLayer.mockRejectedValue(error);

      await expect(
        optimizedDashboardService.getOptimizedUserDashboards(mockUser.id, query)
      ).rejects.toThrow('Cache optimization failed');
    });
  });

  describe('createOptimizedDashboard', () => {
    const createData = {
      title: 'New Dashboard',
      description: 'Test description',
      userId: mockUser.id
    };

    it('should create dashboard with health check and background processing', async () => {
      const newDashboard = { ...mockDashboard, ...createData };
      mockDatabaseOptimizer.performHealthCheck.mockResolvedValue(true);

      // Mock the parent createDashboard method
      jest.spyOn(optimizedDashboardService, 'createDashboard')
        .mockResolvedValue(newDashboard);

      const result = await optimizedDashboardService.createOptimizedDashboard(createData);

      expect(result).toEqual(newDashboard);
      expect(mockDatabaseOptimizer.performHealthCheck).toHaveBeenCalled();
      expect(mockScheduleJob).toHaveBeenCalledWith('default', 'dashboard:cache-warm', {
        dashboardId: newDashboard.id,
        userId: createData.userId
      });
      expect(mockCacheOptimizer.invalidateDistributed).toHaveBeenCalledWith(
        `dashboards:user:${createData.userId}:*`,
        'dashboard-created'
      );
    });

    it('should handle creation failures', async () => {
      const error = new Error('Dashboard creation failed');
      mockDatabaseOptimizer.performHealthCheck.mockResolvedValue(true);

      jest.spyOn(optimizedDashboardService, 'createDashboard')
        .mockRejectedValue(error);

      await expect(
        optimizedDashboardService.createOptimizedDashboard(createData)
      ).rejects.toThrow('Dashboard creation failed');
    });
  });

  describe('batchCreateDashboards', () => {
    const dashboards = [
      { title: 'Dashboard 1', slug: 'dashboard-1' },
      { title: 'Dashboard 2', slug: 'dashboard-2' }
    ];

    it('should use optimized batch upsert for bulk operations', async () => {
      const mockResults = dashboards.map((dash, i) => ({
        id: `dash_${i}`,
        ...dash,
        userId: mockUser.id
      }));

      mockDatabaseOptimizer.batchUpsert.mockResolvedValue(mockResults);

      const result = await optimizedDashboardService.batchCreateDashboards(
        dashboards,
        mockUser.id
      );

      expect(result).toEqual(mockResults);
      expect(mockDatabaseOptimizer.batchUpsert).toHaveBeenCalledWith(
        'dashboard',
        dashboards.map(dash => ({ ...dash, userId: mockUser.id })),
        {
          uniqueField: 'slug',
          batchSize: 50,
          skipDuplicates: true
        }
      );
      expect(mockScheduleJob).toHaveBeenCalledWith('low-priority', 'dashboards:batch-cache-warm', {
        dashboards: mockResults.map(r => r.id),
        userId: mockUser.id
      });
    });

    it('should handle batch operation errors', async () => {
      const error = new Error('Batch operation failed');
      mockDatabaseOptimizer.batchUpsert.mockRejectedValue(error);

      await expect(
        optimizedDashboardService.batchCreateDashboards(dashboards, mockUser.id)
      ).rejects.toThrow('Batch operation failed');
    });
  });

  describe('getOptimizedDashboardAnalytics', () => {
    const query = {
      startDate: '2024-01-01',
      endDate: '2024-01-31',
      granularity: 'day'
    };

    it('should use multi-layer caching for analytics queries', async () => {
      const analyticsData = [
        { period: '2024-01-01', total_views: 100, total_unique_views: 80 }
      ];

      mockCacheOptimizer.getMultiLayer.mockResolvedValue(analyticsData);

      const result = await optimizedDashboardService.getOptimizedDashboardAnalytics(
        mockDashboard.id,
        mockUser.id,
        query
      );

      expect(result).toEqual(analyticsData);
      expect(mockCacheOptimizer.getMultiLayer).toHaveBeenCalledWith(
        expect.any(String), // cache key
        expect.any(Function), // query function
        {
          l1TTL: 300,
          l2TTL: 900,
          strategy: 'analytics-aggregated'
        }
      );
    });

    it('should execute optimized aggregation queries on cache miss', async () => {
      const analyticsData = [
        { period: '2024-01-01', total_views: 100 }
      ];

      mockCacheOptimizer.getMultiLayer.mockImplementation(
        async (key: string, queryFn: Function) => await queryFn()
      );
      mockDatabaseOptimizer.performAggregation.mockResolvedValue(analyticsData);

      const result = await optimizedDashboardService.getOptimizedDashboardAnalytics(
        mockDashboard.id,
        mockUser.id,
        query
      );

      expect(result).toEqual(analyticsData);
      expect(mockDatabaseOptimizer.performAggregation).toHaveBeenCalledWith(
        expect.stringContaining('SELECT'),
        [
          mockDashboard.id,
          expect.any(Date),
          expect.any(Date),
          'day'
        ]
      );
    });
  });

  describe('searchOptimizedDashboards', () => {
    const searchQuery = 'test dashboard';
    const options = { limit: 20, offset: 0 };

    it('should use cached full-text search results', async () => {
      const searchResults = [mockDashboard];
      mockCacheOptimizer.getMultiLayer.mockResolvedValue(searchResults);

      const result = await optimizedDashboardService.searchOptimizedDashboards(
        mockUser.id,
        searchQuery,
        options
      );

      expect(result).toEqual(searchResults);
      expect(mockCacheOptimizer.getMultiLayer).toHaveBeenCalledWith(
        expect.any(String),
        expect.any(Function),
        {
          l1TTL: 180,
          l2TTL: 600,
          strategy: 'search-results'
        }
      );
    });

    it('should execute optimized full-text search on cache miss', async () => {
      const searchResults = [mockDashboard];

      mockCacheOptimizer.getMultiLayer.mockImplementation(
        async (key: string, queryFn: Function) => await queryFn()
      );
      mockDatabaseOptimizer.performFullTextSearch.mockResolvedValue(searchResults);

      const result = await optimizedDashboardService.searchOptimizedDashboards(
        mockUser.id,
        searchQuery,
        options
      );

      expect(result).toEqual(searchResults);
      expect(mockDatabaseOptimizer.performFullTextSearch).toHaveBeenCalledWith(
        'dashboard',
        searchQuery,
        expect.objectContaining({
          fields: ['title', 'description'],
          limit: options.limit,
          offset: options.offset
        })
      );
    });
  });

  describe('warmDashboardCaches', () => {
    const dashboardIds = ['dash_1', 'dash_2', 'dash_3'];

    it('should warm caches for multiple dashboards in batches', async () => {
      // Mock dashboard data
      mockPrisma.dashboard.findFirst = jest.fn()
        .mockResolvedValueOnce(mockDashboard)
        .mockResolvedValueOnce({ ...mockDashboard, id: 'dash_2' })
        .mockResolvedValueOnce({ ...mockDashboard, id: 'dash_3' });

      // Mock getDashboard method
      jest.spyOn(optimizedDashboardService, 'getDashboard')
        .mockResolvedValue(mockDashboard);

      await optimizedDashboardService.warmDashboardCaches(dashboardIds);

      expect(mockCacheOptimizer.warmDashboardCache).toHaveBeenCalledTimes(3);

      // Should warm analytics for common queries
      expect(optimizedDashboardService.getDashboard).toHaveBeenCalledTimes(3);
    });

    it('should handle warming failures gracefully', async () => {
      const error = new Error('Cache warming failed');
      jest.spyOn(optimizedDashboardService, 'getDashboard')
        .mockRejectedValue(error);

      // Should not throw, just log warnings
      await expect(
        optimizedDashboardService.warmDashboardCaches(dashboardIds)
      ).resolves.not.toThrow();
    });

    it('should process dashboards in batches to avoid overwhelming system', async () => {
      const largeDashboardIds = Array.from({ length: 25 }, (_, i) => `dash_${i}`);

      jest.spyOn(optimizedDashboardService, 'getDashboard')
        .mockResolvedValue(mockDashboard);

      await optimizedDashboardService.warmDashboardCaches(largeDashboardIds);

      // Should still warm all dashboards
      expect(mockCacheOptimizer.warmDashboardCache).toHaveBeenCalledTimes(25);
    });
  });

  describe('private helper methods', () => {
    it('should create consistent query hashes', async () => {
      const service = optimizedDashboardService as any;

      const query1 = { page: 1, limit: 10, sortBy: 'title' };
      const query2 = { limit: 10, page: 1, sortBy: 'title' }; // Same data, different order

      const hash1 = service.createQueryHash(query1);
      const hash2 = service.createQueryHash(query2);

      expect(hash1).toBe(hash2);
      expect(hash1).toMatch(/^[a-f0-9]+$/); // Hex string
    });

    it('should build proper dashboard where clauses', async () => {
      const service = optimizedDashboardService as any;

      const whereClause = service.buildDashboardWhereClause(mockUser.id, {
        search: 'test',
        isPublic: true
      });

      expect(whereClause).toEqual({
        OR: [
          { userId: mockUser.id },
          { isPublic: true, isTemplate: false },
          { dashboardShares: { some: { userId: mockUser.id } } }
        ],
        AND: [{
          OR: [
            { title: { contains: 'test', mode: 'insensitive' } },
            { description: { contains: 'test', mode: 'insensitive' } }
          ]
        }],
        isPublic: true
      });
    });
  });
});