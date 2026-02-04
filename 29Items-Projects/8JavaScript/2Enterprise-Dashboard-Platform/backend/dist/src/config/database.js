import { PrismaClient } from '@prisma/client';
import { config, isDevelopment, isTest } from './environment.js';
import { logger } from '@/utils/logger.js';
const prismaConfig = {
    datasources: {
        db: {
            url: config.database.url,
        },
    },
    log: isDevelopment()
        ? [
            { emit: 'event', level: 'query' },
            { emit: 'event', level: 'error' },
            { emit: 'event', level: 'warn' },
            { emit: 'event', level: 'info' },
        ]
        : [
            { emit: 'event', level: 'error' },
            { emit: 'event', level: 'warn' },
        ],
};
export const prisma = new PrismaClient(prismaConfig);
class DatabaseManager {
    static instance;
    connected = false;
    retryAttempts = 0;
    maxRetryAttempts = 5;
    retryDelay = 1000;
    constructor() {
        this.setupEventListeners();
    }
    static getInstance() {
        if (!DatabaseManager.instance) {
            DatabaseManager.instance = new DatabaseManager();
        }
        return DatabaseManager.instance;
    }
    setupEventListeners() {
        if (isDevelopment()) {
            prisma.$on('query', (e) => {
                logger.debug('Database Query', {
                    query: e.query,
                    params: e.params,
                    duration: `${e.duration}ms`,
                });
            });
        }
        prisma.$on('error', (e) => {
            logger.error('Database Error', {
                message: e.message,
                target: e.target,
            });
        });
        prisma.$on('warn', (e) => {
            logger.warn('Database Warning', {
                message: e.message,
                target: e.target,
            });
        });
        process.on('SIGINT', this.disconnect.bind(this));
        process.on('SIGTERM', this.disconnect.bind(this));
    }
    async connect() {
        if (this.connected) {
            return;
        }
        try {
            await prisma.$connect();
            await prisma.$queryRaw `SELECT 1`;
            this.connected = true;
            this.retryAttempts = 0;
            logger.info('Database connected successfully', {
                environment: config.app.env,
                poolSize: config.database.poolSize,
            });
        }
        catch (error) {
            this.connected = false;
            this.retryAttempts++;
            logger.error('Database connection failed', {
                error: error instanceof Error ? error.message : 'Unknown error',
                attempt: this.retryAttempts,
                maxAttempts: this.maxRetryAttempts,
            });
            if (this.retryAttempts < this.maxRetryAttempts) {
                logger.info(`Retrying database connection in ${this.retryDelay}ms...`);
                await new Promise(resolve => setTimeout(resolve, this.retryDelay));
                return this.connect();
            }
            else {
                throw new Error(`Database connection failed after ${this.maxRetryAttempts} attempts`);
            }
        }
    }
    async disconnect() {
        if (!this.connected) {
            return;
        }
        try {
            await prisma.$disconnect();
            this.connected = false;
            logger.info('Database disconnected successfully');
        }
        catch (error) {
            logger.error('Database disconnection error', {
                error: error instanceof Error ? error.message : 'Unknown error',
            });
        }
    }
    async healthCheck() {
        try {
            await prisma.$queryRaw `SELECT 1`;
            return true;
        }
        catch (error) {
            logger.error('Database health check failed', {
                error: error instanceof Error ? error.message : 'Unknown error',
            });
            return false;
        }
    }
    isConnected() {
        return this.connected;
    }
    getPrismaClient() {
        return prisma;
    }
    async transaction(operations) {
        return await prisma.$transaction(operations, {
            maxWait: 5000,
            timeout: 10000,
        });
    }
    async batchExecute(operations) {
        return await prisma.$transaction(operations);
    }
    async queryWithPagination(query, page = 1, limit = 10) {
        const skip = (page - 1) * limit;
        const [data, total] = await Promise.all([
            query({ skip, take: limit }),
            query({}).then(results => Array.isArray(results) ? results.length : 0),
        ]);
        const pages = Math.ceil(total / limit);
        return {
            data,
            total,
            page,
            limit,
            pages,
        };
    }
    getConnectionStats() {
        return {
            connected: this.connected,
            environment: config.app.env,
            poolSize: config.database.poolSize,
        };
    }
}
export const dbManager = DatabaseManager.getInstance();
export const dbUtils = {
    generateSlug: (title) => {
        return title
            .toLowerCase()
            .trim()
            .replace(/[^\w\s-]/g, '')
            .replace(/[\s_-]+/g, '-')
            .replace(/^-+|-+$/g, '');
    },
    recordExists: async (model, id) => {
        try {
            const result = await prisma[model].findUnique({
                where: { id },
                select: { id: true },
            });
            return !!result;
        }
        catch {
            return false;
        }
    },
    softDelete: async (model, id) => {
        await prisma[model].update({
            where: { id },
            data: {
                isActive: false,
                deletedAt: new Date(),
            },
        });
    },
    batchUpsert: async (model, records, uniqueField = 'id') => {
        const operations = records.map((record) => {
            const { [uniqueField]: uniqueValue, ...data } = record;
            return prisma[model].upsert({
                where: { [uniqueField]: uniqueValue },
                update: data,
                create: record,
            });
        });
        return await prisma.$transaction(operations);
    },
};
export const initializeDatabase = async () => {
    try {
        await dbManager.connect();
        if (!isTest()) {
            logger.info('Database initialization completed', {
                environment: config.app.env,
                connectionPoolSize: config.database.poolSize,
            });
        }
    }
    catch (error) {
        logger.error('Database initialization failed', {
            error: error instanceof Error ? error.message : 'Unknown error',
        });
        if (!isDevelopment()) {
            process.exit(1);
        }
    }
};
//# sourceMappingURL=database.js.map