import { z } from 'zod';
import dotenv from 'dotenv';

// Load environment variables
dotenv.config();

// Environment validation schema
const envSchema = z.object({
  // Application
  NODE_ENV: z.enum(['development', 'staging', 'production', 'test']).default('development'),
  PORT: z.string().default('3001').transform(val => parseInt(val)),
  HOST: z.string().default('0.0.0.0'),
  APP_NAME: z.string().default('Enterprise Dashboard Platform'),
  APP_VERSION: z.string().default('1.0.0'),
  APP_URL: z.string().default('http://localhost:3000'),
  API_BASE_URL: z.string().default('http://localhost:3001'),

  // Database
  DATABASE_URL: z.string(),
  DATABASE_POOL_SIZE: z.string().default('10').transform(val => parseInt(val)),
  DATABASE_TIMEOUT: z.string().default('5000').transform(val => parseInt(val)),

  // Redis
  REDIS_URL: z.string().default('redis://localhost:6379'),
  REDIS_PASSWORD: z.string().optional(),
  REDIS_DB: z.string().default('0').transform(val => parseInt(val)),
  REDIS_TTL: z.string().default('3600').transform(val => parseInt(val)),

  // Authentication & Security
  JWT_SECRET: z.string().min(32),
  JWT_EXPIRY: z.string().default('24h'),
  JWT_REFRESH_EXPIRY: z.string().default('7d'),
  BCRYPT_ROUNDS: z.string().default('12').transform(val => parseInt(val)),
  SESSION_SECRET: z.string().default('default-session-secret-change-in-production-min32chars'),
  SESSION_TIMEOUT: z.string().default('86400').transform(val => parseInt(val)),

  // Rate Limiting
  RATE_LIMIT_WINDOW_MS: z.string().default('900000').transform(val => parseInt(val)),
  RATE_LIMIT_MAX_REQUESTS: z.string().default('100').transform(val => parseInt(val)),

  // CORS
  CORS_ORIGIN: z.string().default('http://localhost:3000,http://localhost:5173'),
  CORS_METHODS: z.string().default('GET,POST,PUT,DELETE,OPTIONS'),
  CORS_HEADERS: z.string().default('Content-Type,Authorization,x-csrf-token'),

  // WebSocket
  WS_PORT: z.string().default('3002').transform(val => parseInt(val)),
  WS_HEARTBEAT_INTERVAL: z.string().default('30000').transform(val => parseInt(val)),
  WS_MAX_CONNECTIONS: z.string().default('1000').transform(val => parseInt(val)),
  WS_MAX_CONNECTIONS_PER_IP: z.string().default('10').transform(val => parseInt(val)),
  WS_MAX_CONNECTIONS_PER_USER: z.string().default('5').transform(val => parseInt(val)),
  WS_MAX_MESSAGES_PER_MINUTE: z.string().default('60').transform(val => parseInt(val)),
  WS_MAX_MESSAGES_PER_HOUR: z.string().default('1000').transform(val => parseInt(val)),
  WS_CONNECTION_ATTEMPTS_PER_MINUTE: z.string().default('5').transform(val => parseInt(val)),
  WS_BAN_DURATION: z.string().default('300').transform(val => parseInt(val)),

  // File Upload & Storage
  UPLOAD_MAX_SIZE: z.string().default('10485760').transform(val => parseInt(val)),
  UPLOAD_ALLOWED_TYPES: z.string().default('image/jpeg,image/png,image/gif,text/csv,application/json'),

  // Email (Optional)
  SMTP_HOST: z.string().optional(),
  SMTP_PORT: z.string().optional().transform(val => val ? parseInt(val) : undefined),
  SMTP_USER: z.string().optional(),
  SMTP_PASS: z.string().optional(),
  EMAIL_FROM: z.string().optional(),

  // External APIs
  OPENAI_API_KEY: z.string().optional(),
  GOOGLE_ANALYTICS_KEY: z.string().optional(),
  HUBSPOT_API_KEY: z.string().optional(),

  // Monitoring & Logging
  LOG_LEVEL: z.enum(['error', 'warn', 'info', 'debug']).default('info'),
  LOG_FILE_PATH: z.string().default('./logs'),
  LOG_MAX_SIZE: z.string().default('10m'),
  LOG_MAX_FILES: z.string().default('5').transform(val => parseInt(val)),

  // Azure (Production)
  APPLICATIONINSIGHTS_CONNECTION_STRING: z.string().optional(),
  AZURE_STORAGE_ACCOUNT: z.string().optional(),
  AZURE_STORAGE_KEY: z.string().optional(),
  AZURE_STORAGE_CONTAINER: z.string().default('dashboard-files'),

  // Feature Flags
  ENABLE_REAL_TIME_UPDATES: z.string().default('true').transform(val => val === 'true'),
  ENABLE_ML_FEATURES: z.string().default('true').transform(val => val === 'true'),
  ENABLE_ADVANCED_ANALYTICS: z.string().default('true').transform(val => val === 'true'),
  ENABLE_DRAG_DROP_BUILDER: z.string().default('true').transform(val => val === 'true'),
  ENABLE_EXPORT_FEATURES: z.string().default('true').transform(val => val === 'true'),

  // Development Tools
  ENABLE_DEV_TOOLS: z.string().default('false').transform(val => val === 'true'),
  ENABLE_GRAPHQL_PLAYGROUND: z.string().default('false').transform(val => val === 'true'),
  ENABLE_API_DOCS: z.string().default('false').transform(val => val === 'true'),

  // Performance Settings
  MAX_CONCURRENT_REQUESTS: z.string().default('100').transform(val => parseInt(val)),
  DATABASE_QUERY_TIMEOUT: z.string().default('10000').transform(val => parseInt(val)),
  API_RESPONSE_TIMEOUT: z.string().default('30000').transform(val => parseInt(val)),

  // Security Settings
  ENABLE_HELMET: z.string().default('true').transform(val => val === 'true'),
  ENABLE_RATE_LIMITING: z.string().default('true').transform(val => val === 'true'),
  ENABLE_CORS: z.string().default('true').transform(val => val === 'true'),
  ENABLE_CSRF: z.string().default('true').transform(val => val === 'true'),
  TRUST_PROXY: z.string().default('false').transform(val => val === 'true'),

  // Backup Configuration
  BACKUP_ENABLED: z.string().default('false').transform(val => val === 'true'),
  BACKUP_DIR: z.string().default('./backups'),
  BACKUP_RETENTION_DAYS: z.string().default('30').transform(val => parseInt(val)),
  BACKUP_COMPRESSION: z.string().default('true').transform(val => val === 'true'),
  BACKUP_DAILY_SCHEDULE: z.string().default('0 2 * * *'),
  BACKUP_WEEKLY_SCHEDULE: z.string().default('0 3 * * 0'),
  BACKUP_MONTHLY_SCHEDULE: z.string().default('0 4 1 * *'),
  BACKUP_CLEANUP_SCHEDULE: z.string().default('0 5 * * 0'),
  BACKUP_MAX_SIZE_MB: z.string().default('1000').transform(val => parseInt(val)),
  BACKUP_TIMEOUT_SECONDS: z.string().default('3600').transform(val => parseInt(val)),
  BACKUP_ENCRYPTION_KEY: z.string().optional(),
  BACKUP_REMOTE_BUCKET: z.string().optional(),
  BACKUP_SLACK_WEBHOOK: z.string().optional(),

  // Health Check
  HEALTH_CHECK_ENABLED: z.string().default('true').transform(val => val === 'true'),
  HEALTH_CHECK_PATH: z.string().default('/health'),
  HEALTH_CHECK_DATABASE: z.string().default('true').transform(val => val === 'true'),
  HEALTH_CHECK_REDIS: z.string().default('true').transform(val => val === 'true'),
});

// Validate and parse environment variables
let env: z.infer<typeof envSchema>;

try {
  env = envSchema.parse(process.env);
} catch (error) {
  console.error('❌ Invalid environment variables:');
  if (error instanceof z.ZodError) {
    console.error(error.errors.map(err => `${err.path.join('.')}: ${err.message}`).join('\n'));
  }
  process.exit(1);
}

// Configuration object
export const config = {
  // Application
  app: {
    name: env.APP_NAME,
    version: env.APP_VERSION,
    env: env.NODE_ENV,
    url: env.APP_URL,
    apiBaseUrl: env.API_BASE_URL,
    port: env.PORT,
    host: env.HOST,
  },

  // Database
  database: {
    url: env.DATABASE_URL,
    poolSize: env.DATABASE_POOL_SIZE,
    timeout: env.DATABASE_TIMEOUT,
  },

  // Redis
  redis: {
    url: env.REDIS_URL,
    password: env.REDIS_PASSWORD,
    db: env.REDIS_DB,
    ttl: env.REDIS_TTL,
  },

  // Authentication
  auth: {
    jwtSecret: env.JWT_SECRET,
    jwtExpiry: env.JWT_EXPIRY,
    jwtRefreshExpiry: env.JWT_REFRESH_EXPIRY,
    bcryptRounds: env.BCRYPT_ROUNDS,
    sessionSecret: env.SESSION_SECRET,
    sessionTimeout: env.SESSION_TIMEOUT,
  },

  // Security
  security: {
    rateLimitWindowMs: env.RATE_LIMIT_WINDOW_MS,
    rateLimitMaxRequests: env.RATE_LIMIT_MAX_REQUESTS,
    enableHelmet: env.ENABLE_HELMET,
    enableRateLimit: env.ENABLE_RATE_LIMITING,
    enableCors: env.ENABLE_CORS,
    enableCsrf: env.ENABLE_CSRF,
    trustProxy: env.TRUST_PROXY,
  },

  // CORS
  cors: {
    origin: env.CORS_ORIGIN.split(','),
    methods: env.CORS_METHODS.split(','),
    headers: env.CORS_HEADERS.split(','),
  },

  // WebSocket
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

  // File Upload
  upload: {
    maxSize: env.UPLOAD_MAX_SIZE,
    allowedTypes: env.UPLOAD_ALLOWED_TYPES.split(','),
  },

  // Email
  email: {
    smtp: {
      host: env.SMTP_HOST,
      port: env.SMTP_PORT,
      user: env.SMTP_USER,
      pass: env.SMTP_PASS,
    },
    from: env.EMAIL_FROM,
  },

  // Logging
  logging: {
    level: env.LOG_LEVEL,
    filePath: env.LOG_FILE_PATH,
    maxSize: env.LOG_MAX_SIZE,
    maxFiles: env.LOG_MAX_FILES,
  },

  // Feature Flags
  features: {
    realTimeUpdates: env.ENABLE_REAL_TIME_UPDATES,
    mlFeatures: env.ENABLE_ML_FEATURES,
    advancedAnalytics: env.ENABLE_ADVANCED_ANALYTICS,
    dragDropBuilder: env.ENABLE_DRAG_DROP_BUILDER,
    exportFeatures: env.ENABLE_EXPORT_FEATURES,
  },

  // Development
  development: {
    enableDevTools: env.ENABLE_DEV_TOOLS,
    enableGraphqlPlayground: env.ENABLE_GRAPHQL_PLAYGROUND,
    enableApiDocs: env.ENABLE_API_DOCS,
  },

  // Performance
  performance: {
    maxConcurrentRequests: env.MAX_CONCURRENT_REQUESTS,
    databaseQueryTimeout: env.DATABASE_QUERY_TIMEOUT,
    apiResponseTimeout: env.API_RESPONSE_TIMEOUT,
  },

  // Backup Configuration
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

  // Health Check
  healthCheck: {
    enabled: env.HEALTH_CHECK_ENABLED,
    path: env.HEALTH_CHECK_PATH,
    database: env.HEALTH_CHECK_DATABASE,
    redis: env.HEALTH_CHECK_REDIS,
  },

  // External Services
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

  // Azure
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
} as const;

// Helper functions
export const isDevelopment = () => config.app.env === 'development';
export const isProduction = () => config.app.env === 'production';
export const isTest = () => config.app.env === 'test';
export const isStaging = () => config.app.env === 'staging';

// Export for use in other modules
export { env };
export default config;