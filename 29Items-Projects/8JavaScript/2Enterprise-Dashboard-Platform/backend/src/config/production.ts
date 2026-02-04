import { z } from 'zod';
import { logger } from '@/utils/logger.js';
import { config } from '@/config/environment.js';

/**
 * Production Environment Configuration and Validation
 *
 * This module provides production-specific configuration validation,
 * secrets management, and security hardening for production deployments.
 */

// Production Environment Variable Schema
const productionSchema = z.object({
  // Application Configuration
  NODE_ENV: z.literal('production'),
  APP_NAME: z.string().min(1),
  APP_VERSION: z.string().min(1),
  PORT: z.string().regex(/^\d+$/).transform(Number),
  HOST: z.string().default('0.0.0.0'),

  // Database Configuration (Required in Production)
  DATABASE_URL: z.string().url().refine(url => {
    return url.startsWith('postgresql://') || url.startsWith('postgres://');
  }, 'DATABASE_URL must be a valid PostgreSQL connection string'),
  DATABASE_POOL_SIZE: z.string().regex(/^\d+$/).transform(Number).default('20'),
  DATABASE_TIMEOUT: z.string().regex(/^\d+$/).transform(Number).default('30000'),

  // Redis Configuration (Required in Production)
  REDIS_URL: z.string().url().refine(url => {
    return url.startsWith('redis://') || url.startsWith('rediss://');
  }, 'REDIS_URL must be a valid Redis connection string'),
  REDIS_PASSWORD: z.string().optional(),
  REDIS_TLS: z.enum(['true', 'false']).transform(val => val === 'true').default('false'),

  // JWT and Authentication (Critical for Production)
  JWT_SECRET: z.string().min(32, 'JWT_SECRET must be at least 32 characters long'),
  JWT_EXPIRY: z.string().default('24h'),
  JWT_REFRESH_EXPIRY: z.string().default('7d'),
  SESSION_SECRET: z.string().min(32, 'SESSION_SECRET must be at least 32 characters long'),

  // Security Configuration
  BCRYPT_ROUNDS: z.string().regex(/^\d+$/).transform(Number).default('12'),
  CORS_ORIGIN: z.string().url(),
  HELMET_ENABLED: z.enum(['true', 'false']).transform(val => val === 'true').default('true'),
  RATE_LIMIT_ENABLED: z.enum(['true', 'false']).transform(val => val === 'true').default('true'),
  CSRF_ENABLED: z.enum(['true', 'false']).transform(val => val === 'true').default('true'),

  // SSL/TLS Configuration
  SSL_CERT_PATH: z.string().optional(),
  SSL_KEY_PATH: z.string().optional(),
  FORCE_HTTPS: z.enum(['true', 'false']).transform(val => val === 'true').default('true'),

  // External Services
  EMAIL_SERVICE_API_KEY: z.string().optional(),
  EMAIL_FROM_ADDRESS: z.string().email().optional(),
  MONITORING_API_KEY: z.string().optional(),
  SENTRY_DSN: z.string().url().optional(),

  // Performance Configuration
  MAX_CONCURRENT_REQUESTS: z.string().regex(/^\d+$/).transform(Number).default('1000'),
  REQUEST_TIMEOUT: z.string().regex(/^\d+$/).transform(Number).default('30000'),
  ENABLE_COMPRESSION: z.enum(['true', 'false']).transform(val => val === 'true').default('true'),

  // Logging Configuration
  LOG_LEVEL: z.enum(['error', 'warn', 'info', 'debug']).default('info'),
  LOG_FILE_ENABLED: z.enum(['true', 'false']).transform(val => val === 'true').default('true'),
  LOG_FILE_PATH: z.string().default('/app/logs'),
  LOG_MAX_SIZE: z.string().default('100m'),
  LOG_MAX_FILES: z.string().default('30d'),

  // Health Check Configuration
  HEALTH_CHECK_ENABLED: z.enum(['true', 'false']).transform(val => val === 'true').default('true'),
  HEALTH_CHECK_DATABASE: z.enum(['true', 'false']).transform(val => val === 'true').default('true'),
  HEALTH_CHECK_REDIS: z.enum(['true', 'false']).transform(val => val === 'true').default('true'),

  // Backup Configuration
  BACKUP_ENABLED: z.enum(['true', 'false']).transform(val => val === 'true').default('true'),
  BACKUP_STORAGE_PATH: z.string().default('/app/backups'),
  BACKUP_RETENTION_DAYS: z.string().regex(/^\d+$/).transform(Number).default('30'),
  BACKUP_SCHEDULE: z.string().default('0 2 * * *'), // Daily at 2 AM

  // CDN and Static Assets
  CDN_URL: z.string().url().optional(),
  STATIC_ASSETS_PATH: z.string().default('/app/public'),

  // WebSocket Configuration
  WEBSOCKET_ENABLED: z.enum(['true', 'false']).transform(val => val === 'true').default('true'),
  WEBSOCKET_PORT: z.string().regex(/^\d+$/).transform(Number).optional(),
  WEBSOCKET_PATH: z.string().default('/ws'),
});

export type ProductionConfig = z.infer<typeof productionSchema>;

/**
 * Validate Production Environment
 * Ensures all required environment variables are present and valid
 */
export function validateProductionEnvironment(): ProductionConfig {
  try {
    logger.info('Validating production environment configuration...');

    const validatedConfig = productionSchema.parse(process.env);

    // Additional production-specific validations
    validateSecurityConfiguration(validatedConfig);
    validateDatabaseConfiguration(validatedConfig);
    validatePerformanceConfiguration(validatedConfig);

    logger.info('Production environment validation completed successfully');
    return validatedConfig;

  } catch (error) {
    if (error instanceof z.ZodError) {
      const missingVars = error.errors
        .filter(err => err.code === 'invalid_type' && err.received === 'undefined')
        .map(err => err.path.join('.'));

      const invalidVars = error.errors
        .filter(err => err.code !== 'invalid_type' || err.received !== 'undefined')
        .map(err => ({
          field: err.path.join('.'),
          message: err.message
        }));

      logger.error('Production environment validation failed', {
        missingVariables: missingVars,
        invalidVariables: invalidVars,
        totalErrors: error.errors.length
      });

      throw new Error(`Production environment validation failed:
Missing variables: ${missingVars.join(', ')}
Invalid configurations: ${invalidVars.map(v => `${v.field}: ${v.message}`).join(', ')}`);
    }

    logger.error('Unexpected error during environment validation', { error });
    throw error;
  }
}

/**
 * Validate Security Configuration
 */
function validateSecurityConfiguration(config: ProductionConfig): void {
  const securityIssues: string[] = [];

  // Check JWT secret strength
  if (config.JWT_SECRET.length < 64) {
    securityIssues.push('JWT_SECRET should be at least 64 characters for production');
  }

  // Check session secret strength
  if (config.SESSION_SECRET.length < 64) {
    securityIssues.push('SESSION_SECRET should be at least 64 characters for production');
  }

  // Ensure bcrypt rounds are appropriate for production
  if (config.BCRYPT_ROUNDS < 12) {
    securityIssues.push('BCRYPT_ROUNDS should be at least 12 for production');
  }

  // Validate CORS origin
  if (config.CORS_ORIGIN === '*') {
    securityIssues.push('CORS_ORIGIN should not be wildcard (*) in production');
  }

  // Check HTTPS enforcement
  if (!config.FORCE_HTTPS) {
    securityIssues.push('FORCE_HTTPS should be enabled in production');
  }

  if (securityIssues.length > 0) {
    logger.warn('Security configuration warnings', { issues: securityIssues });

    // In strict production mode, throw error for security issues
    if (process.env.STRICT_SECURITY === 'true') {
      throw new Error(`Security validation failed: ${securityIssues.join(', ')}`);
    }
  }
}

/**
 * Validate Database Configuration
 */
function validateDatabaseConfiguration(config: ProductionConfig): void {
  // Ensure database URL doesn't use default credentials
  if (config.DATABASE_URL.includes('postgres:postgres@') ||
      config.DATABASE_URL.includes('user:password@')) {
    logger.warn('Database appears to use default credentials - ensure proper production credentials are configured');
  }

  // Check pool size is appropriate for production load
  if (config.DATABASE_POOL_SIZE < 10) {
    logger.warn('Database pool size may be too small for production load', {
      currentSize: config.DATABASE_POOL_SIZE,
      recommendedMinimum: 20
    });
  }
}

/**
 * Validate Performance Configuration
 */
function validatePerformanceConfiguration(config: ProductionConfig): void {
  // Check timeout configurations
  if (config.REQUEST_TIMEOUT < 30000) {
    logger.warn('Request timeout may be too aggressive for production', {
      currentTimeout: config.REQUEST_TIMEOUT,
      recommendedMinimum: 30000
    });
  }

  // Verify concurrent request limits
  if (config.MAX_CONCURRENT_REQUESTS < 100) {
    logger.warn('Max concurrent requests may be too low for production load', {
      currentLimit: config.MAX_CONCURRENT_REQUESTS,
      recommendedMinimum: 1000
    });
  }
}

/**
 * Production Health Check
 * Verifies critical systems are operational before startup
 */
export async function performProductionHealthCheck(): Promise<boolean> {
  logger.info('Performing production health checks...');

  const healthChecks = [];

  try {
    // Database connectivity
    if (config.healthCheck?.database) {
      const { dbManager } = await import('@/config/database.js');
      healthChecks.push({
        service: 'database',
        status: await dbManager.healthCheck(),
        critical: true
      });
    }

    // Redis connectivity
    if (config.healthCheck?.redis) {
      const { redisManager } = await import('@/config/redis.js');
      healthChecks.push({
        service: 'redis',
        status: await redisManager.healthCheck(),
        critical: true
      });
    }

    // Check file system permissions for logs and backups
    const fs = await import('fs/promises');

    try {
      await fs.access(process.env.LOG_FILE_PATH || '/app/logs', fs.constants.W_OK);
      healthChecks.push({ service: 'log_directory', status: true, critical: false });
    } catch {
      healthChecks.push({ service: 'log_directory', status: false, critical: false });
    }

    try {
      await fs.access(process.env.BACKUP_STORAGE_PATH || '/app/backups', fs.constants.W_OK);
      healthChecks.push({ service: 'backup_directory', status: true, critical: false });
    } catch {
      healthChecks.push({ service: 'backup_directory', status: false, critical: false });
    }

    const criticalFailures = healthChecks.filter(check => check.critical && !check.status);
    const warnings = healthChecks.filter(check => !check.critical && !check.status);

    if (criticalFailures.length > 0) {
      logger.error('Critical production health check failures', {
        failures: criticalFailures.map(f => f.service),
        allChecks: healthChecks
      });
      return false;
    }

    if (warnings.length > 0) {
      logger.warn('Non-critical production health check warnings', {
        warnings: warnings.map(w => w.service),
        allChecks: healthChecks
      });
    }

    logger.info('Production health checks completed successfully', {
      totalChecks: healthChecks.length,
      passed: healthChecks.filter(c => c.status).length,
      warnings: warnings.length
    });

    return true;

  } catch (error) {
    logger.error('Production health check failed with error', { error });
    return false;
  }
}

/**
 * Production Secrets Validation
 * Ensures secrets meet security requirements
 */
export function validateProductionSecrets(): void {
  const secrets = [
    'JWT_SECRET',
    'SESSION_SECRET',
    'DATABASE_URL',
    'REDIS_URL'
  ];

  const weakSecrets: string[] = [];
  const missingSecrets: string[] = [];

  for (const secretName of secrets) {
    const secretValue = process.env[secretName];

    if (!secretValue) {
      missingSecrets.push(secretName);
      continue;
    }

    // Check for common weak patterns
    if (secretValue.includes('password') ||
        secretValue.includes('secret') ||
        secretValue.includes('123456') ||
        secretValue.length < 32) {
      weakSecrets.push(secretName);
    }
  }

  if (missingSecrets.length > 0 || weakSecrets.length > 0) {
    const errorMessage = `Production secrets validation failed:
${missingSecrets.length > 0 ? `Missing secrets: ${missingSecrets.join(', ')}` : ''}
${weakSecrets.length > 0 ? `Weak secrets detected: ${weakSecrets.join(', ')}` : ''}`;

    logger.error('Production secrets validation failed', {
      missingSecrets,
      weakSecrets
    });

    throw new Error(errorMessage);
  }

  logger.info('Production secrets validation passed');
}

/**
 * Initialize Production Environment
 * Main entry point for production environment setup
 */
export async function initializeProductionEnvironment(): Promise<ProductionConfig> {
  try {
    logger.info('Initializing production environment...');

    // Step 1: Validate environment variables
    const prodConfig = validateProductionEnvironment();

    // Step 2: Validate secrets security
    validateProductionSecrets();

    // Step 3: Perform health checks
    const healthCheckPassed = await performProductionHealthCheck();

    if (!healthCheckPassed) {
      throw new Error('Production health checks failed - cannot start application');
    }

    // Step 4: Set production-specific process settings
    configureProductionProcess();

    logger.info('Production environment initialized successfully', {
      nodeEnv: prodConfig.NODE_ENV,
      appName: prodConfig.APP_NAME,
      version: prodConfig.APP_VERSION,
      port: prodConfig.PORT,
      securityFeaturesEnabled: {
        helmet: prodConfig.HELMET_ENABLED,
        rateLimit: prodConfig.RATE_LIMIT_ENABLED,
        csrf: prodConfig.CSRF_ENABLED,
        forceHttps: prodConfig.FORCE_HTTPS
      }
    });

    return prodConfig;

  } catch (error) {
    logger.error('Failed to initialize production environment', { error });
    throw error;
  }
}

/**
 * Configure Production Process Settings
 */
function configureProductionProcess(): void {
  // Set process title for easier identification
  process.title = process.env.APP_NAME || 'enterprise-dashboard';

  // Handle uncaught exceptions in production
  process.on('uncaughtException', (error) => {
    logger.error('Uncaught Exception in production', {
      error: error.message,
      stack: error.stack,
      pid: process.pid
    });

    // Give some time to flush logs before exiting
    setTimeout(() => {
      process.exit(1);
    }, 1000);
  });

  // Handle unhandled promise rejections
  process.on('unhandledRejection', (reason, promise) => {
    logger.error('Unhandled Promise Rejection in production', {
      reason: reason instanceof Error ? reason.message : reason,
      stack: reason instanceof Error ? reason.stack : undefined,
      promise: promise.toString(),
      pid: process.pid
    });

    // In production, we might want to exit on unhandled rejections
    if (process.env.EXIT_ON_UNHANDLED_REJECTION === 'true') {
      setTimeout(() => {
        process.exit(1);
      }, 1000);
    }
  });

  // Handle graceful shutdown signals
  const gracefulShutdown = (signal: string) => {
    logger.info(`Received ${signal} - initiating graceful shutdown`);

    // Perform cleanup operations here
    // - Close database connections
    // - Finish processing current requests
    // - Save any pending data

    setTimeout(() => {
      logger.info('Graceful shutdown completed');
      process.exit(0);
    }, 5000); // Give 5 seconds for cleanup
  };

  process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
  process.on('SIGINT', () => gracefulShutdown('SIGINT'));

  logger.info('Production process configuration completed');
}