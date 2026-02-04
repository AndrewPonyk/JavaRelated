import Redis from 'ioredis';
export declare const redis: Redis;
export declare const redisSubscriber: Redis;
export declare const redisPublisher: Redis;
declare class RedisManager {
    private static instance;
    private connected;
    private retryAttempts;
    private readonly maxRetryAttempts;
    private constructor();
    static getInstance(): RedisManager;
    private setupEventListeners;
    connect(): Promise<void>;
    disconnect(): Promise<void>;
    healthCheck(): Promise<boolean>;
    isConnected(): boolean;
    getClient(): Redis;
    getSubscriber(): Redis;
    getPublisher(): Redis;
}
export declare const redisManager: RedisManager;
export declare class CacheService {
    private redis;
    private defaultTTL;
    constructor(redisInstance?: Redis, ttl?: number);
    get<T>(key: string): Promise<T | null>;
    set(key: string, value: any, ttl?: number): Promise<boolean>;
    delete(key: string): Promise<boolean>;
    exists(key: string): Promise<boolean>;
    invalidatePattern(pattern: string): Promise<number>;
    getByPattern<T>(pattern: string): Promise<Record<string, T>>;
    increment(key: string, delta?: number): Promise<number>;
    expire(key: string, ttl: number): Promise<boolean>;
    ttl(key: string): Promise<number>;
    hset(key: string, field: string, value: any): Promise<boolean>;
    hget<T>(key: string, field: string): Promise<T | null>;
    hgetall<T>(key: string): Promise<Record<string, T>>;
    lpush(key: string, ...values: any[]): Promise<number>;
    rpop<T>(key: string): Promise<T | null>;
    lrange<T>(key: string, start?: number, stop?: number): Promise<T[]>;
    sadd(key: string, ...members: any[]): Promise<number>;
    smembers<T>(key: string): Promise<T[]>;
    clear(): Promise<void>;
    stats(): Promise<any>;
}
export declare const cacheService: CacheService;
export declare const initializeRedis: () => Promise<void>;
export {};
//# sourceMappingURL=redis.d.ts.map