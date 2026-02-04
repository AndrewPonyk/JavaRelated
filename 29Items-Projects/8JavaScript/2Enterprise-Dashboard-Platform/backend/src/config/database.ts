import { PrismaClient, Prisma } from '@prisma/client';
import { config, isDevelopment, isTest } from './environment.js';
import { logger } from '@/utils/logger.js';

// Prisma client configuration
const prismaConfig: Prisma.PrismaClientOptions = {
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

// Create Prisma client instance
export const prisma = new PrismaClient(prismaConfig);

// Database connection management
class DatabaseManager {
  private static instance: DatabaseManager;
  private connected: boolean = false;
  private retryAttempts: number = 0;
  private readonly maxRetryAttempts: number = 5;
  private readonly retryDelay: number = 1000;

  private constructor() {
    this.setupEventListeners();
  }

  public static getInstance(): DatabaseManager {
    if (!DatabaseManager.instance) {
      DatabaseManager.instance = new DatabaseManager();
    }
    return DatabaseManager.instance;
  }

  private setupEventListeners(): void {
    // Log queries in development
    if (isDevelopment()) {
      (prisma as any).$on('query', (e: Prisma.QueryEvent) => {
        logger.debug('Database Query', {
          query: e.query,
          params: e.params,
          duration: `${e.duration}ms`,
        });
      });
    }

    // Log errors
    (prisma as any).$on('error', (e: Prisma.LogEvent) => {
      logger.error('Database Error', {
        message: e.message,
        target: e.target,
      });
    });

    // Log warnings
    (prisma as any).$on('warn', (e: Prisma.LogEvent) => {
      logger.warn('Database Warning', {
        message: e.message,
        target: e.target,
      });
    });

    // Handle process termination
    process.on('SIGINT', this.disconnect.bind(this));
    process.on('SIGTERM', this.disconnect.bind(this));
  }

  public async connect(): Promise<void> {
    if (this.connected) {
      return;
    }

    try {
      // Test database connection
      await prisma.$connect();

      // Verify connection with a simple query
      await prisma.$queryRaw`SELECT 1`;

      this.connected = true;
      this.retryAttempts = 0;

      logger.info('Database connected successfully', {
        environment: config.app.env,
        poolSize: config.database.poolSize,
      });

    } catch (error) {
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
      } else {
        throw new Error(`Database connection failed after ${this.maxRetryAttempts} attempts`);
      }
    }
  }

  public async disconnect(): Promise<void> {
    if (!this.connected) {
      return;
    }

    try {
      await prisma.$disconnect();
      this.connected = false;
      logger.info('Database disconnected successfully');
    } catch (error) {
      logger.error('Database disconnection error', {
        error: error instanceof Error ? error.message : 'Unknown error',
      });
    }
  }

  public async healthCheck(): Promise<boolean> {
    try {
      await prisma.$queryRaw`SELECT 1`;
      return true;
    } catch (error) {
      logger.error('Database health check failed', {
        error: error instanceof Error ? error.message : 'Unknown error',
      });
      return false;
    }
  }

  public isConnected(): boolean {
    return this.connected;
  }

  public getPrismaClient(): PrismaClient {
    return prisma;
  }

  // Transaction helpers
  public async transaction<T>(
    operations: (tx: Prisma.TransactionClient) => Promise<T>
  ): Promise<T> {
    return await prisma.$transaction(operations);
  }

  // Batch operations
  public async batchExecute(operations: any[]): Promise<any[]> {
    return await prisma.$transaction(operations);
  }

  // Query optimization helpers
  public async queryWithPagination<T>(
    query: (args: { skip?: number; take?: number }) => Promise<T[]>,
    page: number = 1,
    limit: number = 10
  ): Promise<{ data: T[]; total: number; page: number; limit: number; pages: number }> {
    const skip = (page - 1) * limit;

    const [data, total] = await Promise.all([
      query({ skip, take: limit }),
      // This would need to be implemented per query
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

  // Connection pool stats (if available)
  public getConnectionStats(): any {
    // This would require additional instrumentation
    // For now, return basic info
    return {
      connected: this.connected,
      environment: config.app.env,
      poolSize: config.database.poolSize,
    };
  }
}

// Export singleton instance
export const dbManager = DatabaseManager.getInstance();

// Database utilities
export const dbUtils = {
  // Generate unique slug
  generateSlug: (title: string): string => {
    return title
      .toLowerCase()
      .trim()
      .replace(/[^\w\s-]/g, '')
      .replace(/[\s_-]+/g, '-')
      .replace(/^-+|-+$/g, '');
  },

  // Check if record exists
  recordExists: async (model: string, id: string): Promise<boolean> => {
    try {
      const result = await (prisma as any)[model].findUnique({
        where: { id },
        select: { id: true },
      });
      return !!result;
    } catch {
      return false;
    }
  },

  // Soft delete utility
  softDelete: async (model: string, id: string): Promise<void> => {
    await (prisma as any)[model].update({
      where: { id },
      data: {
        isActive: false,
        deletedAt: new Date(),
      },
    });
  },

  // Batch upsert utility
  batchUpsert: async <T>(
    model: string,
    records: T[],
    uniqueField: string = 'id'
  ): Promise<T[]> => {
    const operations = records.map((record: any) => {
      const { [uniqueField]: uniqueValue, ...data } = record;

      return (prisma as any)[model].upsert({
        where: { [uniqueField]: uniqueValue },
        update: data,
        create: record,
      });
    });

    return await prisma.$transaction(operations);
  },
};

// Export commonly used types
export type { PrismaClient, Prisma } from '@prisma/client';

// Connection initialization helper for server startup
export const initializeDatabase = async (): Promise<void> => {
  try {
    await dbManager.connect();

    if (!isTest()) {
      logger.info('Database initialization completed', {
        environment: config.app.env,
        connectionPoolSize: config.database.poolSize,
      });
    }
  } catch (error) {
    logger.error('Database initialization failed', {
      error: error instanceof Error ? error.message : 'Unknown error',
    });

    if (!isDevelopment()) {
      process.exit(1);
    }
  }
};