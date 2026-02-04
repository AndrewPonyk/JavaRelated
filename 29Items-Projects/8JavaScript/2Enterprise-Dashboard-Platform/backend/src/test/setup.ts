import { PrismaClient } from '@prisma/client';
import Redis from 'ioredis';

// Global test setup
const prisma = new PrismaClient({
  datasources: {
    db: {
      url: process.env.DATABASE_URL || 'postgresql://postgres:test123@localhost:5432/test_db',
    },
  },
});

const redis = new Redis(process.env.REDIS_URL || 'redis://localhost:6379', {
  maxRetriesPerRequest: 3,
});

// Global test utilities
declare global {
  var __PRISMA__: PrismaClient;
  var __REDIS__: Redis;
}

global.__PRISMA__ = prisma;
global.__REDIS__ = redis;

// Setup test environment
beforeAll(async () => {
  // Wait for database connection
  await prisma.$connect();

  // Wait for Redis connection
  await new Promise((resolve, reject) => {
    redis.once('ready', resolve);
    redis.once('error', reject);
    setTimeout(() => reject(new Error('Redis connection timeout')), 5000);
  });

  // Clean up test data
  await cleanupDatabase();
});

afterAll(async () => {
  // Clean up after all tests
  await cleanupDatabase();

  // Close connections
  await prisma.$disconnect();
  redis.disconnect();
});

afterEach(async () => {
  // Clean up after each test
  await cleanupTestData();

  // Clear Redis cache
  await redis.flushdb();
});

// Cleanup functions
async function cleanupDatabase() {
  const tablenames = await prisma.$queryRaw<
    Array<{ tablename: string }>
  >`SELECT tablename FROM pg_tables WHERE schemaname='public'`;

  const tables = tablenames
    .map(({ tablename }) => tablename)
    .filter((name) => name !== '_prisma_migrations')
    .map((name) => `"public"."${name}"`)
    .join(', ');

  try {
    if (tables.length > 0) {
      await prisma.$executeRawUnsafe(`TRUNCATE TABLE ${tables} CASCADE;`);
    }
  } catch (error) {
    console.log('Database cleanup error:', error);
  }
}

async function cleanupTestData() {
  // Remove test-specific data
  await prisma.user.deleteMany({
    where: {
      email: {
        contains: 'test',
      },
    },
  });

  await prisma.dashboard.deleteMany({
    where: {
      title: {
        contains: 'Test',
      },
    },
  });

  await prisma.widget.deleteMany({
    where: {
      title: {
        contains: 'Test',
      },
    },
  });
}

// Mock factories
export const createTestUser = async (overrides: any = {}) => {
  return await prisma.user.create({
    data: {
      email: 'test@example.com',
      password: '$2b$10$test.hash.example',
      name: 'Test User',
      role: 'USER',
      ...overrides,
    },
  });
};

export const createTestDashboard = async (userId: string, overrides: any = {}) => {
  return await prisma.dashboard.create({
    data: {
      name: 'Test Dashboard',
      description: 'A test dashboard',
      layout: [],
      settings: {},
      userId,
      ...overrides,
    },
  });
};

export const createTestWidget = async (dashboardId: string, overrides: any = {}) => {
  return await prisma.widget.create({
    data: {
      type: 'CHART',
      title: 'Test Widget',
      config: {},
      position: { x: 0, y: 0, w: 6, h: 4 },
      dashboardId,
      ...overrides,
    },
  });
};

// Mock JWT tokens
export const mockJWT = {
  sign: jest.fn().mockReturnValue('mock-jwt-token'),
  verify: jest.fn().mockReturnValue({ userId: 'test-user-id' }),
};

// Mock Redis methods
export const mockRedis = {
  get: jest.fn(),
  set: jest.fn(),
  del: jest.fn(),
  exists: jest.fn(),
  expire: jest.fn(),
  flushdb: jest.fn(),
};

// Mock external services
export const mockMLService = {
  analyzeData: jest.fn().mockResolvedValue({
    insights: [],
    predictions: [],
    anomalies: [],
  }),
  detectAnomalies: jest.fn().mockResolvedValue([]),
  generatePredictions: jest.fn().mockResolvedValue([]),
};

// Environment setup
process.env.NODE_ENV = 'test';
process.env.JWT_SECRET = 'test-secret';
process.env.JWT_REFRESH_SECRET = 'test-refresh-secret';
process.env.DATABASE_URL = 'postgresql://postgres:test123@localhost:5432/test_db';
process.env.REDIS_URL = 'redis://localhost:6379';