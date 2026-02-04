import Redis from 'ioredis';
import { config, isDevelopment, isTest } from './environment.js';
import { logger } from '@/utils/logger.js';
const redisConfig = {
    host: new URL(config.redis.url).hostname,
    port: parseInt(new URL(config.redis.url).port) || 6379,
    password: config.redis.password,
    db: config.redis.db,
    retryDelayOnFailover: 100,
    maxRetriesPerRequest: 3,
    lazyConnect: true,
    keepAlive: 30000,
    connectTimeout: 10000,
    commandTimeout: 5000,
    retryConnect: true,
    maxRetries: 5,
    ...(config.app.env === 'production' && {
        enableReadyCheck: true,
        enableOfflineQueue: false,
    }),
};
export const redis = new Redis(redisConfig);
export const redisSubscriber = new Redis(redisConfig);
export const redisPublisher = new Redis(redisConfig);
class RedisManager {
    static instance;
    connected = false;
    retryAttempts = 0;
    maxRetryAttempts = 5;
    constructor() {
        this.setupEventListeners();
    }
    static getInstance() {
        if (!RedisManager.instance) {
            RedisManager.instance = new RedisManager();
        }
        return RedisManager.instance;
    }
    setupEventListeners() {
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
        redis.on('reconnecting', (ms) => {
            this.retryAttempts++;
            logger.info(`Redis reconnecting in ${ms}ms...`, {
                attempt: this.retryAttempts,
            });
        });
        process.on('SIGINT', this.disconnect.bind(this));
        process.on('SIGTERM', this.disconnect.bind(this));
    }
    async connect() {
        try {
            await redis.connect();
            await redis.ping();
            logger.info('Redis connected successfully', {
                environment: config.app.env,
                host: redisConfig.host,
                port: redisConfig.port,
                db: redisConfig.db,
            });
        }
        catch (error) {
            logger.error('Redis connection failed', {
                error: error instanceof Error ? error.message : 'Unknown error',
            });
            if (!isDevelopment()) {
                throw error;
            }
        }
    }
    async disconnect() {
        try {
            await Promise.all([
                redis.disconnect(),
                redisSubscriber.disconnect(),
                redisPublisher.disconnect(),
            ]);
            this.connected = false;
            logger.info('Redis disconnected successfully');
        }
        catch (error) {
            logger.error('Redis disconnection error', {
                error: error instanceof Error ? error.message : 'Unknown error',
            });
        }
    }
    async healthCheck() {
        try {
            const result = await redis.ping();
            return result === 'PONG';
        }
        catch (error) {
            logger.error('Redis health check failed', {
                error: error instanceof Error ? error.message : 'Unknown error',
            });
            return false;
        }
    }
    isConnected() {
        return this.connected;
    }
    getClient() {
        return redis;
    }
    getSubscriber() {
        return redisSubscriber;
    }
    getPublisher() {
        return redisPublisher;
    }
}
export const redisManager = RedisManager.getInstance();
export class CacheService {
    redis;
    defaultTTL;
    constructor(redisInstance = redis, ttl = config.redis.ttl) {
        this.redis = redisInstance;
        this.defaultTTL = ttl;
    }
    async get(key) {
        try {
            const value = await this.redis.get(key);
            return value ? JSON.parse(value) : null;
        }
        catch (error) {
            logger.error('Cache get error', { key, error });
            return null;
        }
    }
    async set(key, value, ttl = this.defaultTTL) {
        try {
            const serialized = JSON.stringify(value);
            await this.redis.setex(key, ttl, serialized);
            return true;
        }
        catch (error) {
            logger.error('Cache set error', { key, error });
            return false;
        }
    }
    async delete(key) {
        try {
            const deleted = await this.redis.del(key);
            return deleted > 0;
        }
        catch (error) {
            logger.error('Cache delete error', { key, error });
            return false;
        }
    }
    async exists(key) {
        try {
            const exists = await this.redis.exists(key);
            return exists > 0;
        }
        catch (error) {
            logger.error('Cache exists error', { key, error });
            return false;
        }
    }
    async invalidatePattern(pattern) {
        try {
            const keys = await this.redis.keys(pattern);
            if (keys.length === 0)
                return 0;
            const deleted = await this.redis.del(...keys);
            logger.info(`Invalidated ${deleted} cache entries`, { pattern });
            return deleted;
        }
        catch (error) {
            logger.error('Cache invalidate pattern error', { pattern, error });
            return 0;
        }
    }
    async getByPattern(pattern) {
        try {
            const keys = await this.redis.keys(pattern);
            if (keys.length === 0)
                return {};
            const values = await this.redis.mget(...keys);
            const result = {};
            keys.forEach((key, index) => {
                const value = values[index];
                if (value) {
                    try {
                        result[key] = JSON.parse(value);
                    }
                    catch {
                    }
                }
            });
            return result;
        }
        catch (error) {
            logger.error('Cache get by pattern error', { pattern, error });
            return {};
        }
    }
    async increment(key, delta = 1) {
        try {
            return await this.redis.incrby(key, delta);
        }
        catch (error) {
            logger.error('Cache increment error', { key, delta, error });
            return 0;
        }
    }
    async expire(key, ttl) {
        try {
            const result = await this.redis.expire(key, ttl);
            return result === 1;
        }
        catch (error) {
            logger.error('Cache expire error', { key, ttl, error });
            return false;
        }
    }
    async ttl(key) {
        try {
            return await this.redis.ttl(key);
        }
        catch (error) {
            logger.error('Cache TTL error', { key, error });
            return -1;
        }
    }
    async hset(key, field, value) {
        try {
            const serialized = JSON.stringify(value);
            await this.redis.hset(key, field, serialized);
            return true;
        }
        catch (error) {
            logger.error('Cache hset error', { key, field, error });
            return false;
        }
    }
    async hget(key, field) {
        try {
            const value = await this.redis.hget(key, field);
            return value ? JSON.parse(value) : null;
        }
        catch (error) {
            logger.error('Cache hget error', { key, field, error });
            return null;
        }
    }
    async hgetall(key) {
        try {
            const hash = await this.redis.hgetall(key);
            const result = {};
            Object.entries(hash).forEach(([field, value]) => {
                try {
                    result[field] = JSON.parse(value);
                }
                catch {
                }
            });
            return result;
        }
        catch (error) {
            logger.error('Cache hgetall error', { key, error });
            return {};
        }
    }
    async lpush(key, ...values) {
        try {
            const serialized = values.map(v => JSON.stringify(v));
            return await this.redis.lpush(key, ...serialized);
        }
        catch (error) {
            logger.error('Cache lpush error', { key, error });
            return 0;
        }
    }
    async rpop(key) {
        try {
            const value = await this.redis.rpop(key);
            return value ? JSON.parse(value) : null;
        }
        catch (error) {
            logger.error('Cache rpop error', { key, error });
            return null;
        }
    }
    async lrange(key, start = 0, stop = -1) {
        try {
            const values = await this.redis.lrange(key, start, stop);
            return values.map(v => {
                try {
                    return JSON.parse(v);
                }
                catch {
                    return v;
                }
            });
        }
        catch (error) {
            logger.error('Cache lrange error', { key, start, stop, error });
            return [];
        }
    }
    async sadd(key, ...members) {
        try {
            const serialized = members.map(m => JSON.stringify(m));
            return await this.redis.sadd(key, ...serialized);
        }
        catch (error) {
            logger.error('Cache sadd error', { key, error });
            return 0;
        }
    }
    async smembers(key) {
        try {
            const members = await this.redis.smembers(key);
            return members.map(m => {
                try {
                    return JSON.parse(m);
                }
                catch {
                    return m;
                }
            });
        }
        catch (error) {
            logger.error('Cache smembers error', { key, error });
            return [];
        }
    }
    async clear() {
        try {
            await this.redis.flushdb();
            logger.info('Cache cleared');
        }
        catch (error) {
            logger.error('Cache clear error', { error });
        }
    }
    async stats() {
        try {
            const info = await this.redis.info('memory');
            const keyspace = await this.redis.info('keyspace');
            return { memory: info, keyspace };
        }
        catch (error) {
            logger.error('Cache stats error', { error });
            return null;
        }
    }
}
export const cacheService = new CacheService();
export const initializeRedis = async () => {
    try {
        await redisManager.connect();
        if (!isTest()) {
            logger.info('Redis initialization completed', {
                environment: config.app.env,
                database: config.redis.db,
            });
        }
    }
    catch (error) {
        logger.error('Redis initialization failed', {
            error: error instanceof Error ? error.message : 'Unknown error',
        });
        if (!isDevelopment()) {
            process.exit(1);
        }
    }
};
//# sourceMappingURL=redis.js.map