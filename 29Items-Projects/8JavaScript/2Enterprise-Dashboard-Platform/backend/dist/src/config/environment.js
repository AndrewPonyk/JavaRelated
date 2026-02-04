import { z } from 'zod';
import dotenv from 'dotenv';
dotenv.config();
const envSchema = z.object({
    NODE_ENV: z.enum(['development', 'staging', 'production', 'test']).default('development'),
    PORT: z.string().transform(val => parseInt(val)).default(3001),
    HOST: z.string().default('0.0.0.0'),
    APP_NAME: z.string().default('Enterprise Dashboard Platform'),
    APP_VERSION: z.string().default('1.0.0'),
    APP_URL: z.string().url().default('http://localhost:3000'),
    API_BASE_URL: z.string().url().default('http://localhost:3001'),
    DATABASE_URL: z.string().url(),
    DATABASE_POOL_SIZE: z.string().transform(val => parseInt(val)).default(10),
    DATABASE_TIMEOUT: z.string().transform(val => parseInt(val)).default(5000),
    REDIS_URL: z.string().url().default('redis://localhost:6379'),
    REDIS_PASSWORD: z.string().optional(),
    REDIS_DB: z.string().transform(val => parseInt(val)).default(0),
    REDIS_TTL: z.string().transform(val => parseInt(val)).default(3600),
    JWT_SECRET: z.string().min(32),
    JWT_EXPIRY: z.string().default('24h'),
    JWT_REFRESH_EXPIRY: z.string().default('7d'),
    BCRYPT_ROUNDS: z.string().transform(val => parseInt(val)).default(12),
    SESSION_SECRET: z.string().min(32),
    SESSION_TIMEOUT: z.string().transform(val => parseInt(val)).default(86400),
    RATE_LIMIT_WINDOW_MS: z.string().transform(val => parseInt(val)).default(900000),
    RATE_LIMIT_MAX_REQUESTS: z.string().transform(val => parseInt(val)).default(100),
    CORS_ORIGIN: z.string().default('http://localhost:3000,http://localhost:5173'),
    CORS_METHODS: z.string().default('GET,POST,PUT,DELETE,OPTIONS'),
    CORS_HEADERS: z.string().default('Content-Type,Authorization'),
    WS_PORT: z.string().transform(val => parseInt(val)).default(3002),
    WS_HEARTBEAT_INTERVAL: z.string().transform(val => parseInt(val)).default(30000),
    WS_MAX_CONNECTIONS: z.string().transform(val => parseInt(val)).default(1000),
    WS_MAX_CONNECTIONS_PER_IP: z.string().transform(val => parseInt(val)).default(10),
    WS_MAX_CONNECTIONS_PER_USER: z.string().transform(val => parseInt(val)).default(5),
    WS_MAX_MESSAGES_PER_MINUTE: z.string().transform(val => parseInt(val)).default(60),
    WS_MAX_MESSAGES_PER_HOUR: z.string().transform(val => parseInt(val)).default(1000),
    WS_CONNECTION_ATTEMPTS_PER_MINUTE: z.string().transform(val => parseInt(val)).default(5),
    WS_BAN_DURATION: z.string().transform(val => parseInt(val)).default(300),
    UPLOAD_MAX_SIZE: z.string().transform(val => parseInt(val)).default(10485760),
    UPLOAD_ALLOWED_TYPES: z.string().default('image/jpeg,image/png,image/gif,text/csv,application/json'),
    SMTP_HOST: z.string().optional(),
    SMTP_PORT: z.string().transform(val => val ? parseInt(val) : undefined).optional(),
    SMTP_USER: z.string().optional(),
    SMTP_PASS: z.string().optional(),
    EMAIL_FROM: z.string().optional(),
    OPENAI_API_KEY: z.string().optional(),
    GOOGLE_ANALYTICS_KEY: z.string().optional(),
    HUBSPOT_API_KEY: z.string().optional(),
    LOG_LEVEL: z.enum(['error', 'warn', 'info', 'debug']).default('info'),
    LOG_FILE_PATH: z.string().default('./logs'),
    LOG_MAX_SIZE: z.string().default('10m'),
    LOG_MAX_FILES: z.string().transform(val => parseInt(val)).default(5),
    APPLICATIONINSIGHTS_CONNECTION_STRING: z.string().optional(),
    AZURE_STORAGE_ACCOUNT: z.string().optional(),
    AZURE_STORAGE_KEY: z.string().optional(),
    AZURE_STORAGE_CONTAINER: z.string().default('dashboard-files'),
    ENABLE_REAL_TIME_UPDATES: z.string().transform(val => val === 'true').default(true),
    ENABLE_ML_FEATURES: z.string().transform(val => val === 'true').default(true),
    ENABLE_ADVANCED_ANALYTICS: z.string().transform(val => val === 'true').default(true),
    ENABLE_DRAG_DROP_BUILDER: z.string().transform(val => val === 'true').default(true),
    ENABLE_EXPORT_FEATURES: z.string().transform(val => val === 'true').default(true),
    ENABLE_DEV_TOOLS: z.string().transform(val => val === 'true').default(false),
    ENABLE_GRAPHQL_PLAYGROUND: z.string().transform(val => val === 'true').default(false),
    ENABLE_API_DOCS: z.string().transform(val => val === 'true').default(false),
    MAX_CONCURRENT_REQUESTS: z.string().transform(val => parseInt(val)).default(100),
    DATABASE_QUERY_TIMEOUT: z.string().transform(val => parseInt(val)).default(10000),
    API_RESPONSE_TIMEOUT: z.string().transform(val => parseInt(val)).default(30000),
    ENABLE_HELMET: z.string().transform(val => val === 'true').default(true),
    ENABLE_RATE_LIMITING: z.string().transform(val => val === 'true').default(true),
    ENABLE_CORS: z.string().transform(val => val === 'true').default(true),
    ENABLE_CSRF: z.string().transform(val => val === 'true').default(true),
    TRUST_PROXY: z.string().transform(val => val === 'true').default(false),
    BACKUP_ENABLED: z.string().transform(val => val === 'true').default(false),
    BACKUP_DIR: z.string().default('./backups'),
    BACKUP_RETENTION_DAYS: z.string().transform(val => parseInt(val)).default(30),
    BACKUP_COMPRESSION: z.string().transform(val => val === 'true').default(true),
    BACKUP_DAILY_SCHEDULE: z.string().default('0 2 * * *'),
    BACKUP_WEEKLY_SCHEDULE: z.string().default('0 3 * * 0'),
    BACKUP_MONTHLY_SCHEDULE: z.string().default('0 4 1 * *'),
    BACKUP_CLEANUP_SCHEDULE: z.string().default('0 5 * * 0'),
    BACKUP_MAX_SIZE_MB: z.string().transform(val => parseInt(val)).default(1000),
    BACKUP_TIMEOUT_SECONDS: z.string().transform(val => parseInt(val)).default(3600),
    BACKUP_ENCRYPTION_KEY: z.string().optional(),
    BACKUP_REMOTE_BUCKET: z.string().optional(),
    BACKUP_SLACK_WEBHOOK: z.string().optional(),
    HEALTH_CHECK_ENABLED: z.string().transform(val => val === 'true').default(true),
    HEALTH_CHECK_PATH: z.string().default('/health'),
    HEALTH_CHECK_DATABASE: z.string().transform(val => val === 'true').default(true),
    HEALTH_CHECK_REDIS: z.string().transform(val => val === 'true').default(true),
});
let env;
try {
    env = envSchema.parse(process.env);
}
catch (error) {
    console.error('❌ Invalid environment variables:');
    if (error instanceof z.ZodError) {
        console.error(error.errors.map(err => `${err.path.join('.')}: ${err.message}`).join('\n'));
    }
    process.exit(1);
}
export const config = {
    app: {
        name: env.APP_NAME,
        version: env.APP_VERSION,
        env: env.NODE_ENV,
        url: env.APP_URL,
        apiBaseUrl: env.API_BASE_URL,
        port: env.PORT,
        host: env.HOST,
    },
    database: {
        url: env.DATABASE_URL,
        poolSize: env.DATABASE_POOL_SIZE,
        timeout: env.DATABASE_TIMEOUT,
    },
    redis: {
        url: env.REDIS_URL,
        password: env.REDIS_PASSWORD,
        db: env.REDIS_DB,
        ttl: env.REDIS_TTL,
    },
    auth: {
        jwtSecret: env.JWT_SECRET,
        jwtExpiry: env.JWT_EXPIRY,
        jwtRefreshExpiry: env.JWT_REFRESH_EXPIRY,
        bcryptRounds: env.BCRYPT_ROUNDS,
        sessionSecret: env.SESSION_SECRET,
        sessionTimeout: env.SESSION_TIMEOUT,
    },
    security: {
        rateLimitWindowMs: env.RATE_LIMIT_WINDOW_MS,
        rateLimitMaxRequests: env.RATE_LIMIT_MAX_REQUESTS,
        enableHelmet: env.ENABLE_HELMET,
        enableRateLimit: env.ENABLE_RATE_LIMITING,
        enableCors: env.ENABLE_CORS,
        enableCsrf: env.ENABLE_CSRF,
        trustProxy: env.TRUST_PROXY,
    },
    cors: {
        origin: env.CORS_ORIGIN.split(','),
        methods: env.CORS_METHODS.split(','),
        headers: env.CORS_HEADERS.split(','),
    },
    websocket: {
        port: env.WS_PORT,
        heartbeatInterval: env.WS_HEARTBEAT_INTERVAL,
        maxConnections: env.WS_MAX_CONNECTIONS,
        maxConnectionsPerIP: env.WS_MAX_CONNECTIONS_PER_IP,
        maxConnectionsPerUser: env.WS_MAX_CONNECTIONS_PER_USER,
        maxMessagesPerMinute: env.WS_MAX_MESSAGES_PER_MINUTE,
        maxMessagesPerHour: env.WS_MAX_MESSAGES_PER_HOUR,
        connectionAttemptsPerMinute: env.WS_CONNECTION_ATTEMPTS_PER_MINUTE,
        banDuration: env.WS_BAN_DURATION,
    },
    upload: {
        maxSize: env.UPLOAD_MAX_SIZE,
        allowedTypes: env.UPLOAD_ALLOWED_TYPES.split(','),
    },
    email: {
        smtp: {
            host: env.SMTP_HOST,
            port: env.SMTP_PORT,
            user: env.SMTP_USER,
            pass: env.SMTP_PASS,
        },
        from: env.EMAIL_FROM,
    },
    logging: {
        level: env.LOG_LEVEL,
        filePath: env.LOG_FILE_PATH,
        maxSize: env.LOG_MAX_SIZE,
        maxFiles: env.LOG_MAX_FILES,
    },
    features: {
        realTimeUpdates: env.ENABLE_REAL_TIME_UPDATES,
        mlFeatures: env.ENABLE_ML_FEATURES,
        advancedAnalytics: env.ENABLE_ADVANCED_ANALYTICS,
        dragDropBuilder: env.ENABLE_DRAG_DROP_BUILDER,
        exportFeatures: env.ENABLE_EXPORT_FEATURES,
    },
    development: {
        enableDevTools: env.ENABLE_DEV_TOOLS,
        enableGraphqlPlayground: env.ENABLE_GRAPHQL_PLAYGROUND,
        enableApiDocs: env.ENABLE_API_DOCS,
    },
    performance: {
        maxConcurrentRequests: env.MAX_CONCURRENT_REQUESTS,
        databaseQueryTimeout: env.DATABASE_QUERY_TIMEOUT,
        apiResponseTimeout: env.API_RESPONSE_TIMEOUT,
    },
    backup: {
        enabled: env.BACKUP_ENABLED,
        dir: env.BACKUP_DIR,
        retentionDays: env.BACKUP_RETENTION_DAYS,
        compression: env.BACKUP_COMPRESSION,
        dailySchedule: env.BACKUP_DAILY_SCHEDULE,
        weeklySchedule: env.BACKUP_WEEKLY_SCHEDULE,
        monthlySchedule: env.BACKUP_MONTHLY_SCHEDULE,
        cleanupSchedule: env.BACKUP_CLEANUP_SCHEDULE,
        maxSizeMB: env.BACKUP_MAX_SIZE_MB,
        timeoutSeconds: env.BACKUP_TIMEOUT_SECONDS,
        encryptionKey: env.BACKUP_ENCRYPTION_KEY,
        remoteBucket: env.BACKUP_REMOTE_BUCKET,
        slackWebhook: env.BACKUP_SLACK_WEBHOOK,
    },
    healthCheck: {
        enabled: env.HEALTH_CHECK_ENABLED,
        path: env.HEALTH_CHECK_PATH,
        database: env.HEALTH_CHECK_DATABASE,
        redis: env.HEALTH_CHECK_REDIS,
    },
    external: {
        openai: {
            apiKey: env.OPENAI_API_KEY,
        },
        googleAnalytics: {
            key: env.GOOGLE_ANALYTICS_KEY,
        },
        hubspot: {
            apiKey: env.HUBSPOT_API_KEY,
        },
    },
    azure: {
        applicationInsights: {
            connectionString: env.APPLICATIONINSIGHTS_CONNECTION_STRING,
        },
        storage: {
            account: env.AZURE_STORAGE_ACCOUNT,
            key: env.AZURE_STORAGE_KEY,
            container: env.AZURE_STORAGE_CONTAINER,
        },
    },
};
export const isDevelopment = () => config.app.env === 'development';
export const isProduction = () => config.app.env === 'production';
export const isTest = () => config.app.env === 'test';
export const isStaging = () => config.app.env === 'staging';
export { env };
export default config;
//# sourceMappingURL=environment.js.map