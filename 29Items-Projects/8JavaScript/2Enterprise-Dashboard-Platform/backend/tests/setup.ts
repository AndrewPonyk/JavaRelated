import { beforeAll, afterAll, beforeEach, afterEach } from '@jest/globals';
import { PrismaClient } from '@prisma/client';
import Redis from 'ioredis';

// Mock implementations
const mockPrisma = {
  user: {
    findUnique: jest.fn(),
    findMany: jest.fn(),
    create: jest.fn(),
    update: jest.fn(),
    delete: jest.fn(),
    count: jest.fn(),
  },
  dashboard: {
    findUnique: jest.fn(),
    findMany: jest.fn(),
    findFirst: jest.fn(),
    create: jest.fn(),
    update: jest.fn(),
    delete: jest.fn(),
    count: jest.fn(),
    deleteMany: jest.fn(),
  },
  widget: {
    findUnique: jest.fn(),
    findMany: jest.fn(),
    create: jest.fn(),
    update: jest.fn(),
    delete: jest.fn(),
    deleteMany: jest.fn(),
  },
  dashboardShare: {
    findUnique: jest.fn(),
    findMany: jest.fn(),
    create: jest.fn(),
    update: jest.fn(),
    delete: jest.fn(),
    deleteMany: jest.fn(),
  },
  dashboardAnalytics: {
    findMany: jest.fn(),
    create: jest.fn(),
    update: jest.fn(),
    upsert: jest.fn(),
    deleteMany: jest.fn(),
  },
  activityLog: {
    create: jest.fn(),
    findMany: jest.fn(),
    deleteMany: jest.fn(),
  },
  $transaction: jest.fn(),
  $queryRaw: jest.fn(),
  $queryRawUnsafe: jest.fn(),
  $executeRaw: jest.fn(),
  $connect: jest.fn(),
  $disconnect: jest.fn(),
};

const mockRedis = {
  get: jest.fn(),
  set: jest.fn(),
  setex: jest.fn(),
  del: jest.fn(),
  exists: jest.fn(),
  keys: jest.fn(),
  mget: jest.fn(),
  mset: jest.fn(),
  expire: jest.fn(),
  ttl: jest.fn(),
  incr: jest.fn(),
  incrby: jest.fn(),
  hset: jest.fn(),
  hget: jest.fn(),
  hgetall: jest.fn(),
  hkeys: jest.fn(),
  hvals: jest.fn(),
  hdel: jest.fn(),
  lpush: jest.fn(),
  rpop: jest.fn(),
  lrange: jest.fn(),
  sadd: jest.fn(),
  smembers: jest.fn(),
  srem: jest.fn(),
  zadd: jest.fn(),
  zrange: jest.fn(),
  zrangebyscore: jest.fn(),
  zrem: jest.fn(),
  zremrangebyscore: jest.fn(),
  zcard: jest.fn(),
  bzpopmin: jest.fn(),
  flushdb: jest.fn(),
  info: jest.fn(),
  ping: jest.fn(),
  connect: jest.fn(),
  disconnect: jest.fn(),
  publish: jest.fn(),
  subscribe: jest.fn(),
  pipeline: jest.fn(() => ({
    exec: jest.fn(),
    get: jest.fn(),
    set: jest.fn(),
    del: jest.fn(),
  })),
  on: jest.fn(),
};

// Mock modules
jest.mock('@prisma/client', () => ({
  PrismaClient: jest.fn(() => mockPrisma),
}));

jest.mock('ioredis', () => jest.fn(() => mockRedis));

jest.mock('@/config/database.js', () => ({
  prisma: mockPrisma,
  dbManager: {
    connect: jest.fn(),
    disconnect: jest.fn(),
    healthCheck: jest.fn(),
    isConnected: jest.fn(),
    getPrismaClient: jest.fn(() => mockPrisma),
    transaction: jest.fn(),
    batchExecute: jest.fn(),
  },
  initializeDatabase: jest.fn(),
}));

jest.mock('@/config/redis.js', () => ({
  redis: mockRedis,
  redisSubscriber: mockRedis,
  redisPublisher: mockRedis,
  redisManager: {
    connect: jest.fn(),
    disconnect: jest.fn(),
    healthCheck: jest.fn(),
    isConnected: jest.fn(),
    getClient: jest.fn(() => mockRedis),
    getSubscriber: jest.fn(() => mockRedis),
    getPublisher: jest.fn(() => mockRedis),
  },
  initializeRedis: jest.fn(),
}));

jest.mock('@/config/environment.js', () => ({
  config: {
    app: {
      name: 'Test App',
      version: '1.0.0',
      env: 'test',
      url: 'http://localhost:3000',
      apiBaseUrl: 'http://localhost:3001',
      port: 3001,
      host: '0.0.0.0',
    },
    database: {
      url: 'postgresql://test:test@localhost:5432/test',
      poolSize: 10,
      timeout: 5000,
    },
    redis: {
      url: 'redis://localhost:6379',
      password: undefined,
      db: 0,
      ttl: 3600,
    },
    auth: {
      jwtSecret: 'test-secret-key-32-chars-long!',
      jwtExpiry: '24h',
      jwtRefreshExpiry: '7d',
      bcryptRounds: 10,
      sessionSecret: 'test-session-secret-32-chars-long',
      sessionTimeout: 86400,
    },
    security: {
      rateLimitWindowMs: 900000,
      rateLimitMaxRequests: 100,
      enableHelmet: true,
      enableRateLimit: true,
      enableCors: true,
      enableCsrf: true,
      trustProxy: false,
    },
    performance: {
      maxConcurrentRequests: 100,
      databaseQueryTimeout: 10000,
      apiResponseTimeout: 30000,
    },
    backup: {
      enabled: false,
      dir: './test-backups',
      retentionDays: 7,
      compression: true,
    },
  },
  isDevelopment: () => false,
  isProduction: () => false,
  isTest: () => true,
  isStaging: () => false,
}));

// Mock logger
jest.mock('@/utils/logger.js', () => ({
  logger: {
    info: jest.fn(),
    warn: jest.fn(),
    error: jest.fn(),
    debug: jest.fn(),
  },
  createRequestLogger: jest.fn(() => (req: any, res: any, next: any) => next()),
  logBusinessEvent: jest.fn(),
  logCacheOperation: jest.fn(),
}));

// Test utilities
export const mockUser = {
  id: 'usr_12345',
  email: 'test@example.com',
  firstName: 'Test',
  lastName: 'User',
  role: 'USER',
  isActive: true,
  createdAt: new Date('2024-01-01T00:00:00Z'),
  updatedAt: new Date('2024-01-01T00:00:00Z'),
  lastLoginAt: new Date('2024-01-01T00:00:00Z'),
};

export const mockDashboard = {
  id: 'dash_12345',
  title: 'Test Dashboard',
  description: 'Test description',
  slug: 'test-dashboard',
  isPublic: false,
  isTemplate: false,
  layout: '{}',
  settings: '{}',
  userId: 'usr_12345',
  createdAt: new Date('2024-01-01T00:00:00Z'),
  updatedAt: new Date('2024-01-01T00:00:00Z'),
};

export const mockWidget = {
  id: 'wgt_12345',
  title: 'Test Widget',
  type: 'chart',
  position: '{}',
  config: '{}',
  data: null,
  isVisible: true,
  dashboardId: 'dash_12345',
  userId: 'usr_12345',
  createdAt: new Date('2024-01-01T00:00:00Z'),
  updatedAt: new Date('2024-01-01T00:00:00Z'),
};

export const clearAllMocks = () => {
  // Clear Prisma mocks
  Object.values(mockPrisma).forEach((mock: any) => {
    if (typeof mock === 'object' && mock !== null) {
      Object.values(mock).forEach((method: any) => {
        if (jest.isMockFunction(method)) {
          method.mockClear();
        }
      });
    } else if (jest.isMockFunction(mock)) {
      mock.mockClear();
    }
  });

  // Clear Redis mocks
  Object.values(mockRedis).forEach((mock: any) => {
    if (jest.isMockFunction(mock)) {
      mock.mockClear();
    }
  });
};

// Setup and teardown
beforeAll(async () => {
  // Set test environment
  process.env.NODE_ENV = 'test';
  process.env.DATABASE_URL = 'postgresql://test:test@localhost:5432/test';
  process.env.REDIS_URL = 'redis://localhost:6379';
  process.env.JWT_SECRET = 'test-secret-key-32-chars-long!';
  process.env.SESSION_SECRET = 'test-session-secret-32-chars-long';
});

beforeEach(() => {
  clearAllMocks();
});

afterEach(() => {
  jest.clearAllTimers();
});

afterAll(async () => {
  // Cleanup
});

// Global test helpers
global.mockPrisma = mockPrisma;
global.mockRedis = mockRedis;
global.mockUser = mockUser;
global.mockDashboard = mockDashboard;
global.mockWidget = mockWidget;
global.clearAllMocks = clearAllMocks;

// Extend Jest matchers
expect.extend({
  toBeValidUUID(received: string) {
    const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
    const pass = uuidRegex.test(received);

    if (pass) {
      return {
        message: () => `expected ${received} not to be a valid UUID`,
        pass: true,
      };
    } else {
      return {
        message: () => `expected ${received} to be a valid UUID`,
        pass: false,
      };
    }
  },

  toBeValidDate(received: any) {
    const pass = received instanceof Date && !isNaN(received.getTime());

    if (pass) {
      return {
        message: () => `expected ${received} not to be a valid Date`,
        pass: true,
      };
    } else {
      return {
        message: () => `expected ${received} to be a valid Date`,
        pass: false,
      };
    }
  },
});

declare global {
  namespace jest {
    interface Matchers<R> {
      toBeValidUUID(): R;
      toBeValidDate(): R;
    }
  }

  var mockPrisma: any;
  var mockRedis: any;
  var mockUser: any;
  var mockDashboard: any;
  var mockWidget: any;
  var clearAllMocks: () => void;
}