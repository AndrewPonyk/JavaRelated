import { CacheOptimizer } from '@/utils/performance/cacheOptimizer.js';
import { mockRedis } from '../../setup.js';

// Mock Redis
jest.mock('ioredis', () => jest.fn(() => mockRedis));

describe('CacheOptimizer', () => {
  let cacheOptimizer: CacheOptimizer;

  beforeEach(() => {
    cacheOptimizer = new CacheOptimizer();
    clearAllMocks();
  });

  describe('getMultiLayer', () => {
    const testKey = 'test:key:123';
    const testData = { id: '123', name: 'Test Data' };
    const queryFunction = jest.fn().mockResolvedValue(testData);

    it('should return data from L1 cache (memory) when available', async () => {
      // Pre-populate L1 cache
      const cacheInstance = cacheOptimizer as any;
      cacheInstance.l1Cache.set(testKey, {
        data: testData,
        expiry: Date.now() + 60000
      });

      const result = await cacheOptimizer.getMultiLayer(
        testKey,
        queryFunction,
        { l1TTL: 60, l2TTL: 300, strategy: 'test' }
      );

      expect(result).toEqual(testData);
      expect(queryFunction).not.toHaveBeenCalled();
      expect(mockRedis.get).not.toHaveBeenCalled();
    });

    it('should return data from L2 cache (Redis) when L1 misses', async () => {
      mockRedis.get.mockResolvedValue(JSON.stringify(testData));

      const result = await cacheOptimizer.getMultiLayer(
        testKey,
        queryFunction,
        { l1TTL: 60, l2TTL: 300, strategy: 'test' }
      );

      expect(result).toEqual(testData);
      expect(mockRedis.get).toHaveBeenCalledWith(testKey);
      expect(queryFunction).not.toHaveBeenCalled();

      // Should populate L1 cache with the data
      const cacheInstance = cacheOptimizer as any;
      const l1Entry = cacheInstance.l1Cache.get(testKey);
      expect(l1Entry).toBeDefined();
      expect(l1Entry.data).toEqual(testData);
    });

    it('should execute query function when both caches miss', async () => {
      mockRedis.get.mockResolvedValue(null);

      const result = await cacheOptimizer.getMultiLayer(
        testKey,
        queryFunction,
        { l1TTL: 60, l2TTL: 300, strategy: 'test' }
      );

      expect(result).toEqual(testData);
      expect(queryFunction).toHaveBeenCalled();
      expect(mockRedis.get).toHaveBeenCalledWith(testKey);

      // Should populate both caches
      expect(mockRedis.setex).toHaveBeenCalledWith(
        testKey,
        300,
        JSON.stringify(testData)
      );

      const cacheInstance = cacheOptimizer as any;
      const l1Entry = cacheInstance.l1Cache.get(testKey);
      expect(l1Entry).toBeDefined();
      expect(l1Entry.data).toEqual(testData);
    });

    it('should handle L1 cache expiration', async () => {
      // Pre-populate L1 cache with expired data
      const cacheInstance = cacheOptimizer as any;
      cacheInstance.l1Cache.set(testKey, {
        data: testData,
        expiry: Date.now() - 1000 // Expired 1 second ago
      });

      mockRedis.get.mockResolvedValue(JSON.stringify(testData));

      const result = await cacheOptimizer.getMultiLayer(
        testKey,
        queryFunction,
        { l1TTL: 60, l2TTL: 300, strategy: 'test' }
      );

      expect(result).toEqual(testData);
      expect(mockRedis.get).toHaveBeenCalledWith(testKey);
      expect(queryFunction).not.toHaveBeenCalled();
    });

    it('should handle Redis parsing errors gracefully', async () => {
      mockRedis.get.mockResolvedValue('invalid-json');

      const result = await cacheOptimizer.getMultiLayer(
        testKey,
        queryFunction,
        { l1TTL: 60, l2TTL: 300, strategy: 'test' }
      );

      expect(result).toEqual(testData);
      expect(queryFunction).toHaveBeenCalled();
    });

    it('should handle query function errors', async () => {
      const queryError = new Error('Database connection failed');
      queryFunction.mockRejectedValue(queryError);
      mockRedis.get.mockResolvedValue(null);

      await expect(
        cacheOptimizer.getMultiLayer(
          testKey,
          queryFunction,
          { l1TTL: 60, l2TTL: 300, strategy: 'test' }
        )
      ).rejects.toThrow('Database connection failed');

      expect(queryFunction).toHaveBeenCalled();
      expect(mockRedis.setex).not.toHaveBeenCalled();
    });

    it('should use different strategies for different cache policies', async () => {
      const strategies = ['user-profile', 'dashboard-list', 'analytics-aggregated'];

      for (const strategy of strategies) {
        mockRedis.get.mockResolvedValue(null);
        queryFunction.mockClear();
        queryFunction.mockResolvedValue(testData);

        await cacheOptimizer.getMultiLayer(
          `${strategy}:test`,
          queryFunction,
          { l1TTL: 60, l2TTL: 300, strategy }
        );

        expect(queryFunction).toHaveBeenCalled();
      }
    });
  });

  describe('invalidateDistributed', () => {
    it('should invalidate cache keys matching pattern', async () => {
      const pattern = 'user:123:*';
      const matchingKeys = ['user:123:profile', 'user:123:dashboards', 'user:123:settings'];

      mockRedis.keys.mockResolvedValue(matchingKeys);
      mockRedis.del.mockResolvedValue(matchingKeys.length);

      await cacheOptimizer.invalidateDistributed(pattern, 'user-update');

      expect(mockRedis.keys).toHaveBeenCalledWith(pattern);
      expect(mockRedis.del).toHaveBeenCalledWith(...matchingKeys);

      // Should also clear L1 cache for matching keys
      const cacheInstance = cacheOptimizer as any;
      matchingKeys.forEach(key => {
        expect(cacheInstance.l1Cache.has(key)).toBe(false);
      });
    });

    it('should handle no matching keys gracefully', async () => {
      const pattern = 'nonexistent:*';
      mockRedis.keys.mockResolvedValue([]);

      await cacheOptimizer.invalidateDistributed(pattern, 'cleanup');

      expect(mockRedis.keys).toHaveBeenCalledWith(pattern);
      expect(mockRedis.del).not.toHaveBeenCalled();
    });

    it('should handle Redis errors during invalidation', async () => {
      const pattern = 'user:123:*';
      const redisError = new Error('Redis connection failed');
      mockRedis.keys.mockRejectedValue(redisError);

      // Should not throw, just log the error
      await expect(
        cacheOptimizer.invalidateDistributed(pattern, 'user-update')
      ).resolves.not.toThrow();
    });

    it('should limit batch size for large key sets', async () => {
      const pattern = 'large:*';
      const manyKeys = Array.from({ length: 1000 }, (_, i) => `large:key:${i}`);

      mockRedis.keys.mockResolvedValue(manyKeys);
      mockRedis.del.mockResolvedValue(100); // Mock batch size

      await cacheOptimizer.invalidateDistributed(pattern, 'bulk-cleanup');

      // Should call del multiple times with batches
      expect(mockRedis.del).toHaveBeenCalled();
      const delCalls = (mockRedis.del as jest.Mock).mock.calls;
      expect(delCalls.length).toBeGreaterThan(1);

      // Each batch should not exceed 100 keys
      delCalls.forEach(call => {
        expect(call.length).toBeLessThanOrEqual(100);
      });
    });
  });

  describe('warmDashboardCache', () => {
    const dashboardId = 'dash_123';
    const dashboardData = {
      id: dashboardId,
      title: 'Test Dashboard',
      widgets: [{ id: 'widget_1' }]
    };

    it('should warm dashboard cache with metadata', async () => {
      await cacheOptimizer.warmDashboardCache(dashboardId, dashboardData);

      // Should cache dashboard metadata
      expect(mockRedis.setex).toHaveBeenCalledWith(
        `dashboard:${dashboardId}`,
        expect.any(Number),
        JSON.stringify(dashboardData)
      );

      // Should also populate L1 cache
      const cacheInstance = cacheOptimizer as any;
      const l1Entry = cacheInstance.l1Cache.get(`dashboard:${dashboardId}`);
      expect(l1Entry).toBeDefined();
      expect(l1Entry.data).toEqual(dashboardData);
    });

    it('should warm related cache keys', async () => {
      await cacheOptimizer.warmDashboardCache(dashboardId, dashboardData);

      // Should warm multiple related keys
      expect(mockRedis.setex).toHaveBeenCalledWith(
        `dashboard:${dashboardId}:widgets`,
        expect.any(Number),
        JSON.stringify(dashboardData.widgets)
      );

      expect(mockRedis.setex).toHaveBeenCalledWith(
        `dashboard:${dashboardId}:metadata`,
        expect.any(Number),
        JSON.stringify({
          id: dashboardData.id,
          title: dashboardData.title,
          widgetCount: dashboardData.widgets.length
        })
      );
    });

    it('should handle warming errors gracefully', async () => {
      const redisError = new Error('Redis connection failed');
      mockRedis.setex.mockRejectedValue(redisError);

      // Should not throw, just log the error
      await expect(
        cacheOptimizer.warmDashboardCache(dashboardId, dashboardData)
      ).resolves.not.toThrow();
    });
  });

  describe('warmupCache', () => {
    it('should warm frequently accessed data', async () => {
      await cacheOptimizer.warmupCache(false);

      // Should warm common cache patterns
      expect(mockRedis.setex).toHaveBeenCalledWith(
        'system:health',
        expect.any(Number),
        expect.any(String)
      );

      expect(mockRedis.setex).toHaveBeenCalledWith(
        'config:app',
        expect.any(Number),
        expect.any(String)
      );
    });

    it('should perform full warmup when requested', async () => {
      await cacheOptimizer.warmupCache(true);

      // Should warm more cache keys for full warmup
      const setexCalls = (mockRedis.setex as jest.Mock).mock.calls;
      expect(setexCalls.length).toBeGreaterThan(2);

      // Should include analytics warmup
      const hasAnalyticsKey = setexCalls.some(call =>
        call[0].includes('analytics:')
      );
      expect(hasAnalyticsKey).toBe(true);
    });

    it('should handle partial warmup failures', async () => {
      mockRedis.setex
        .mockResolvedValueOnce('OK') // First key succeeds
        .mockRejectedValueOnce(new Error('Redis error')) // Second key fails
        .mockResolvedValueOnce('OK'); // Third key succeeds

      // Should not throw despite partial failures
      await expect(
        cacheOptimizer.warmupCache(false)
      ).resolves.not.toThrow();
    });
  });

  describe('getCacheStats', () => {
    it('should return comprehensive cache statistics', async () => {
      // Populate some L1 cache data
      const cacheInstance = cacheOptimizer as any;
      cacheInstance.l1Cache.set('key1', { data: 'data1', expiry: Date.now() + 60000 });
      cacheInstance.l1Cache.set('key2', { data: 'data2', expiry: Date.now() + 60000 });

      // Mock Redis info
      mockRedis.info.mockResolvedValue(
        'used_memory:1048576\r\nused_memory_human:1.00M\r\nconnected_clients:10'
      );

      const stats = await cacheOptimizer.getCacheStats();

      expect(stats).toEqual({
        l1Cache: {
          size: 2,
          memoryUsage: expect.any(Number),
          hitRate: expect.any(Number),
          missRate: expect.any(Number)
        },
        l2Cache: {
          memoryUsage: 1048576,
          memoryUsageHuman: '1.00M',
          connectedClients: 10
        },
        performance: {
          totalHits: expect.any(Number),
          totalMisses: expect.any(Number),
          hitRate: expect.any(Number),
          avgResponseTime: expect.any(Number)
        }
      });
    });

    it('should handle Redis info parsing errors', async () => {
      mockRedis.info.mockResolvedValue('invalid_info_format');

      const stats = await cacheOptimizer.getCacheStats();

      expect(stats.l2Cache).toEqual({
        memoryUsage: 0,
        memoryUsageHuman: '0B',
        connectedClients: 0
      });
    });

    it('should track hit/miss ratios accurately', async () => {
      const cacheInstance = cacheOptimizer as any;

      // Simulate cache hits and misses
      cacheInstance.metrics = {
        hits: 80,
        misses: 20,
        totalRequests: 100
      };

      mockRedis.info.mockResolvedValue('used_memory:0');

      const stats = await cacheOptimizer.getCacheStats();

      expect(stats.performance.hitRate).toBe(80);
      expect(stats.performance.totalHits).toBe(80);
      expect(stats.performance.totalMisses).toBe(20);
    });
  });

  describe('memory management', () => {
    it('should evict expired L1 cache entries', async () => {
      const cacheInstance = cacheOptimizer as any;

      // Add expired entry
      cacheInstance.l1Cache.set('expired:key', {
        data: 'expired data',
        expiry: Date.now() - 1000
      });

      // Add valid entry
      cacheInstance.l1Cache.set('valid:key', {
        data: 'valid data',
        expiry: Date.now() + 60000
      });

      // Trigger cleanup
      await cacheInstance.cleanupExpiredL1();

      expect(cacheInstance.l1Cache.has('expired:key')).toBe(false);
      expect(cacheInstance.l1Cache.has('valid:key')).toBe(true);
    });

    it('should limit L1 cache size', async () => {
      const cacheInstance = cacheOptimizer as any;
      const maxSize = cacheInstance.maxL1Size || 1000;

      // Fill cache beyond limit
      for (let i = 0; i < maxSize + 100; i++) {
        cacheInstance.l1Cache.set(`key:${i}`, {
          data: `data:${i}`,
          expiry: Date.now() + 60000
        });
      }

      // Trigger size management
      await cacheInstance.manageL1Size();

      expect(cacheInstance.l1Cache.size).toBeLessThanOrEqual(maxSize);
    });
  });

  describe('cache strategy optimization', () => {
    it('should apply different TTLs based on strategy', async () => {
      const strategies = [
        { name: 'user-profile', expectedL2TTL: 1800 },
        { name: 'dashboard-metadata', expectedL2TTL: 900 },
        { name: 'analytics-aggregated', expectedL2TTL: 3600 }
      ];

      for (const strategy of strategies) {
        mockRedis.get.mockResolvedValue(null);
        const queryFunction = jest.fn().mockResolvedValue({ data: 'test' });

        await cacheOptimizer.getMultiLayer(
          `${strategy.name}:test`,
          queryFunction,
          { l1TTL: 60, l2TTL: 300, strategy: strategy.name }
        );

        // Strategy should override default TTL
        expect(mockRedis.setex).toHaveBeenCalledWith(
          expect.any(String),
          strategy.expectedL2TTL,
          expect.any(String)
        );
      }
    });

    it('should implement intelligent cache warming based on access patterns', async () => {
      const cacheInstance = cacheOptimizer as any;

      // Simulate access patterns
      cacheInstance.accessPatterns = {
        'user:123:profile': { count: 100, lastAccess: Date.now() },
        'user:456:profile': { count: 50, lastAccess: Date.now() - 60000 },
        'user:789:profile': { count: 5, lastAccess: Date.now() - 3600000 }
      };

      await cacheInstance.intelligentWarmup();

      // Should prioritize frequently accessed data
      const setexCalls = (mockRedis.setex as jest.Mock).mock.calls;
      const warmedKeys = setexCalls.map(call => call[0]);

      expect(warmedKeys).toContain('user:123:profile');
      expect(warmedKeys).toContain('user:456:profile');
      expect(warmedKeys).not.toContain('user:789:profile'); // Too old/infrequent
    });
  });
});