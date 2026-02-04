import { PrismaClient } from '@prisma/client';
import Redis from 'ioredis';
const prisma = new PrismaClient({
    datasources: {
        db: {
            url: process.env.DATABASE_URL || 'postgresql://postgres:test123@localhost:5432/test_db',
        },
    },
});
const redis = new Redis(process.env.REDIS_URL || 'redis://localhost:6379', {
    maxRetriesPerRequest: 3,
    retryDelayOnFailover: 100,
});
global.__PRISMA__ = prisma;
global.__REDIS__ = redis;
beforeAll(async () => {
    await prisma.$connect();
    await new Promise((resolve, reject) => {
        redis.once('ready', resolve);
        redis.once('error', reject);
        setTimeout(() => reject(new Error('Redis connection timeout')), 5000);
    });
    await cleanupDatabase();
});
afterAll(async () => {
    await cleanupDatabase();
    await prisma.$disconnect();
    redis.disconnect();
});
afterEach(async () => {
    await cleanupTestData();
    await redis.flushdb();
});
async function cleanupDatabase() {
    const tablenames = await prisma.$queryRaw `SELECT tablename FROM pg_tables WHERE schemaname='public'`;
    const tables = tablenames
        .map(({ tablename }) => tablename)
        .filter((name) => name !== '_prisma_migrations')
        .map((name) => `"public"."${name}"`)
        .join(', ');
    try {
        if (tables.length > 0) {
            await prisma.$executeRawUnsafe(`TRUNCATE TABLE ${tables} CASCADE;`);
        }
    }
    catch (error) {
        console.log('Database cleanup error:', error);
    }
}
async function cleanupTestData() {
    await prisma.user.deleteMany({
        where: {
            email: {
                contains: 'test',
            },
        },
    });
    await prisma.dashboard.deleteMany({
        where: {
            name: {
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
export const createTestUser = async (overrides = {}) => {
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
export const createTestDashboard = async (userId, overrides = {}) => {
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
export const createTestWidget = async (dashboardId, overrides = {}) => {
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
export const mockJWT = {
    sign: jest.fn().mockReturnValue('mock-jwt-token'),
    verify: jest.fn().mockReturnValue({ userId: 'test-user-id' }),
};
export const mockRedis = {
    get: jest.fn(),
    set: jest.fn(),
    del: jest.fn(),
    exists: jest.fn(),
    expire: jest.fn(),
    flushdb: jest.fn(),
};
export const mockMLService = {
    analyzeData: jest.fn().mockResolvedValue({
        insights: [],
        predictions: [],
        anomalies: [],
    }),
    detectAnomalies: jest.fn().mockResolvedValue([]),
    generatePredictions: jest.fn().mockResolvedValue([]),
};
process.env.NODE_ENV = 'test';
process.env.JWT_SECRET = 'test-secret';
process.env.JWT_REFRESH_SECRET = 'test-refresh-secret';
process.env.DATABASE_URL = 'postgresql://postgres:test123@localhost:5432/test_db';
process.env.REDIS_URL = 'redis://localhost:6379';
//# sourceMappingURL=setup.js.map