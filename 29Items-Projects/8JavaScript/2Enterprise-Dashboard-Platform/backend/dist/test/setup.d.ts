import { PrismaClient } from '@prisma/client';
import Redis from 'ioredis';
declare global {
    var __PRISMA__: PrismaClient;
    var __REDIS__: Redis;
}
export declare const createTestUser: (overrides?: any) => Promise<any>;
export declare const createTestDashboard: (userId: string, overrides?: any) => Promise<any>;
export declare const createTestWidget: (dashboardId: string, overrides?: any) => Promise<any>;
export declare const mockJWT: {
    sign: jest.Mock<any, any, any>;
    verify: jest.Mock<any, any, any>;
};
export declare const mockRedis: {
    get: jest.Mock<any, any, any>;
    set: jest.Mock<any, any, any>;
    del: jest.Mock<any, any, any>;
    exists: jest.Mock<any, any, any>;
    expire: jest.Mock<any, any, any>;
    flushdb: jest.Mock<any, any, any>;
};
export declare const mockMLService: {
    analyzeData: jest.Mock<any, any, any>;
    detectAnomalies: jest.Mock<any, any, any>;
    generatePredictions: jest.Mock<any, any, any>;
};
//# sourceMappingURL=setup.d.ts.map