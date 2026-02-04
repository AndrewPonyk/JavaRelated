import Redis, { RedisOptions } from 'ioredis';
import { config, isDevelopment, isTest } from './environment.js';
import { logger } from '@/utils/logger.js';

// Redis configuration
const redisConfig: RedisOptions = {
  host: new URL(config.redis.url).hostname,
  port: parseInt(new URL(config.redis.url).port) || 6379,
  password: config.redis.password,
  db: config.redis.db,
  maxRetriesPerRequest: 3,
  lazyConnect: true,
  keepAlive: 30000,

  // Connection timeout
  connectTimeout: 10000,
  commandTimeout: 5000,

  // Additional options for production
  ...(config.app.env === 'production' && {
    enableReadyCheck: true,
    enableOfflineQueue: true, // Allow queuing commands while connecting
  }),
};

// Create Redis instances
export const redis = new Redis(redisConfig);
export const redisSubscriber = new Redis(redisConfig);
export const redisPublisher = new Redis(redisConfig);

// Redis connection manager
class RedisManager {
  private static instance: RedisManager;
  private connected: boolean = false;
  private retryAttempts: number = 0;
  private readonly maxRetryAttempts: number = 5;

  private constructor() {
    this.setupEventListeners();
  }

  public static getInstance(): RedisManager {
    if (!RedisManager.instance) {
      RedisManager.instance = new RedisManager();
    }
    return RedisManager.instance;
  }

  private setupEventListeners(): void {
    // Connection events
    redis.on('connect', () => {
      logger.info('Redis connection established');
    });

    redis.on('ready', () => {
      this.connected = true;
      this.retryAttempts = 0;
      logger.info('Redis client ready', {
        environment: config.app.env,
        database: config.redis.db,
      });
    });

    redis.on('error', (error) => {
      this.connected = false;
      logger.error('Redis connection error', {
        error: error.message,
        attempt: this.retryAttempts,
      });
    });

    redis.on('close', () => {
      this.connected = false;
      logger.warn('Redis connection closed');
    });

    redis.on('reconnecting', (ms: number) => {
      this.retryAttempts++;
      logger.info(`Redis reconnecting in ${ms}ms...`, {
        attempt: this.retryAttempts,
      });
    });

    // Handle process termination
    process.on('SIGINT', this.disconnect.bind(this));
    process.on('SIGTERM', this.disconnect.bind(this));
  }

  public async connect(): Promise<void> {
    try {
      await redis.connect();

      // Test connection
      await redis.ping();

      logger.info('Redis connected successfully', {
        environment: config.app.env,
        host: redisConfig.host,
        port: redisConfig.port,
        db: redisConfig.db,
      });

    } catch (error) {
      logger.error('Redis connection failed', {
        error: error instanceof Error ? error.message : 'Unknown error',
      });

      if (!isDevelopment()) {
        throw error;
      }
    }
  }

  public async disconnect(): Promise<void> {
    try {
      await Promise.all([
        redis.disconnect(),
        redisSubscriber.disconnect(),
        redisPublisher.disconnect(),
      ]);

      this.connected = false;
      logger.info('Redis disconnected successfully');
    } catch (error) {
      logger.error('Redis disconnection error', {
        error: error instanceof Error ? error.message : 'Unknown error',
      });
    }
  }

  public async healthCheck(): Promise<boolean> {
    try {
      const result = await redis.ping();
      return result === 'PONG';
    } catch (error) {
      logger.error('Redis health check failed', {
        error: error instanceof Error ? error.message : 'Unknown error',
      });
      return false;
    }
  }

  public isConnected(): boolean {
    return this.connected;
  }

  public getClient(): Redis {
    return redis;
  }

  public getSubscriber(): Redis {
    return redisSubscriber;
  }

  public getPublisher(): Redis {
    return redisPublisher;
  }
}

// Export singleton instance
export const redisManager = RedisManager.getInstance();

// Cache service class
export class CacheService {
  private redis: Redis;
  private defaultTTL: number;

  constructor(redisInstance: Redis = redis, ttl: number = config.redis.ttl) {
    this.redis = redisInstance;
    this.defaultTTL = ttl;
  }

  // Basic cache operations
  async get<T>(key: string): Promise<T | null> {
    try {
      const value = await this.redis.get(key);
      return value ? JSON.parse(value) : null;
    } catch (error) {
      logger.error('Cache get error', { key, error });
      return null;
    }
  }

  async set(key: string, value: any, ttl: number = this.defaultTTL): Promise<boolean> {
    try {
      const serialized = JSON.stringify(value);
      await this.redis.setex(key, ttl, serialized);
      return true;
    } catch (error) {
      logger.error('Cache set error', { key, error });
      return false;
    }
  }

  async delete(key: string): Promise<boolean> {
    try {
      const deleted = await this.redis.del(key);
      return deleted > 0;
    } catch (error) {
      logger.error('Cache delete error', { key, error });
      return false;
    }
  }

  async exists(key: string): Promise<boolean> {
    try {
      const exists = await this.redis.exists(key);
      return exists > 0;
    } catch (error) {
      logger.error('Cache exists error', { key, error });
      return false;
    }
  }

  // Pattern-based operations
  async keys(pattern: string): Promise<string[]> {
    try {
      return await this.redis.keys(pattern);
    } catch (error) {
      logger.error('Cache keys error', { pattern, error });
      return [];
    }
  }

  async deletePattern(pattern: string): Promise<number> {
    return this.invalidatePattern(pattern);
  }

  async invalidatePattern(pattern: string): Promise<number> {
    try {
      const keys = await this.redis.keys(pattern);
      if (keys.length === 0) return 0;

      const deleted = await this.redis.del(...keys);
      logger.info(`Invalidated ${deleted} cache entries`, { pattern });
      return deleted;
    } catch (error) {
      logger.error('Cache invalidate pattern error', { pattern, error });
      return 0;
    }
  }

  async getByPattern<T>(pattern: string): Promise<Record<string, T>> {
    try {
      const keys = await this.redis.keys(pattern);
      if (keys.length === 0) return {};

      const values = await this.redis.mget(...keys);
      const result: Record<string, T> = {};

      keys.forEach((key, index) => {
        const value = values[index];
        if (value) {
          try {
            result[key] = JSON.parse(value);
          } catch {
            // Skip invalid JSON
          }
        }
      });

      return result;
    } catch (error) {
      logger.error('Cache get by pattern error', { pattern, error });
      return {};
    }
  }

  // Advanced cache operations
  async increment(key: string, delta: number = 1): Promise<number> {
    try {
      return await this.redis.incrby(key, delta);
    } catch (error) {
      logger.error('Cache increment error', { key, delta, error });
      return 0;
    }
  }

  async expire(key: string, ttl: number): Promise<boolean> {
    try {
      const result = await this.redis.expire(key, ttl);
      return result === 1;
    } catch (error) {
      logger.error('Cache expire error', { key, ttl, error });
      return false;
    }
  }

  async ttl(key: string): Promise<number> {
    try {
      return await this.redis.ttl(key);
    } catch (error) {
      logger.error('Cache TTL error', { key, error });
      return -1;
    }
  }

  // Hash operations
  async hset(key: string, field: string, value: any): Promise<boolean> {
    try {
      const serialized = JSON.stringify(value);
      await this.redis.hset(key, field, serialized);
      return true;
    } catch (error) {
      logger.error('Cache hset error', { key, field, error });
      return false;
    }
  }

  async hget<T>(key: string, field: string): Promise<T | null> {
    try {
      const value = await this.redis.hget(key, field);
      return value ? JSON.parse(value) : null;
    } catch (error) {
      logger.error('Cache hget error', { key, field, error });
      return null;
    }
  }

  async hgetall<T>(key: string): Promise<Record<string, T>> {
    try {
      const hash = await this.redis.hgetall(key);
      const result: Record<string, T> = {};

      Object.entries(hash).forEach(([field, value]) => {
        try {
          result[field] = JSON.parse(value);
        } catch {
          // Skip invalid JSON
        }
      });

      return result;
    } catch (error) {
      logger.error('Cache hgetall error', { key, error });
      return {};
    }
  }

  // List operations
  async lpush(key: string, ...values: any[]): Promise<number> {
    try {
      const serialized = values.map(v => JSON.stringify(v));
      return await this.redis.lpush(key, ...serialized);
    } catch (error) {
      logger.error('Cache lpush error', { key, error });
      return 0;
    }
  }

  async rpop<T>(key: string): Promise<T | null> {
    try {
      const value = await this.redis.rpop(key);
      return value ? JSON.parse(value) : null;
    } catch (error) {
      logger.error('Cache rpop error', { key, error });
      return null;
    }
  }

  async lrange<T>(key: string, start: number = 0, stop: number = -1): Promise<T[]> {
    try {
      const values = await this.redis.lrange(key, start, stop);
      return values.map(v => {
        try {
          return JSON.parse(v);
        } catch {
          return v as T;
        }
      });
    } catch (error) {
      logger.error('Cache lrange error', { key, start, stop, error });
      return [];
    }
  }

  // Set operations
  async sadd(key: string, ...members: any[]): Promise<number> {
    try {
      const serialized = members.map(m => JSON.stringify(m));
      return await this.redis.sadd(key, ...serialized);
    } catch (error) {
      logger.error('Cache sadd error', { key, error });
      return 0;
    }
  }

  async smembers<T>(key: string): Promise<T[]> {
    try {
      const members = await this.redis.smembers(key);
      return members.map(m => {
        try {
          return JSON.parse(m);
        } catch {
          return m as T;
        }
      });
    } catch (error) {
      logger.error('Cache smembers error', { key, error });
      return [];
    }
  }

  // Utility methods
  async clear(): Promise<void> {
    try {
      await this.redis.flushdb();
      logger.info('Cache cleared');
    } catch (error) {
      logger.error('Cache clear error', { error });
    }
  }

  async stats(): Promise<any> {
    try {
      const info = await this.redis.info('memory');
      const keyspace = await this.redis.info('keyspace');
      return { memory: info, keyspace };
    } catch (error) {
      logger.error('Cache stats error', { error });
      return null;
    }
  }
}

// Create cache service instance
export const cacheService = new CacheService();

// Redis initialization helper
export const initializeRedis = async (): Promise<void> => {
  try {
    await redisManager.connect();

    if (!isTest()) {
      logger.info('Redis initialization completed', {
        environment: config.app.env,
        database: config.redis.db,
      });
    }
  } catch (error) {
    logger.error('Redis initialization failed', {
      error: error instanceof Error ? error.message : 'Unknown error',
    });

    if (!isDevelopment()) {
      process.exit(1);
    }
  }
};