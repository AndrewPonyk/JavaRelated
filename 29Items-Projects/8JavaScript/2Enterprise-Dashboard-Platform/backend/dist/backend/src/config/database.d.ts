import { PrismaClient, Prisma } from '@prisma/client';
export declare const prisma: any;
declare class DatabaseManager {
    private static instance;
    private connected;
    private retryAttempts;
    private readonly maxRetryAttempts;
    private readonly retryDelay;
    private constructor();
    static getInstance(): DatabaseManager;
    private setupEventListeners;
    connect(): Promise<void>;
    disconnect(): Promise<void>;
    healthCheck(): Promise<boolean>;
    isConnected(): boolean;
    getPrismaClient(): PrismaClient;
    transaction<T>(operations: (tx: Prisma.TransactionClient) => Promise<T>): Promise<T>;
    batchExecute(operations: any[]): Promise<any[]>;
    queryWithPagination<T>(query: (args: {
        skip?: number;
        take?: number;
    }) => Promise<T[]>, page?: number, limit?: number): Promise<{
        data: T[];
        total: number;
        page: number;
        limit: number;
        pages: number;
    }>;
    getConnectionStats(): any;
}
export declare const dbManager: DatabaseManager;
export declare const dbUtils: {
    generateSlug: (title: string) => string;
    recordExists: (model: string, id: string) => Promise<boolean>;
    softDelete: (model: string, id: string) => Promise<void>;
    batchUpsert: <T>(model: string, records: T[], uniqueField?: string) => Promise<T[]>;
};
export type { PrismaClient, Prisma } from '@prisma/client';
export declare const initializeDatabase: () => Promise<void>;
//# sourceMappingURL=database.d.ts.map