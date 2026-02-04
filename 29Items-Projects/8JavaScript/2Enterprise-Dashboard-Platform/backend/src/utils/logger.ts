import winston from 'winston';
import path from 'path';
import fs from 'fs';
import { config, isDevelopment, isProduction, isTest } from '@/config/environment.js';

// Ensure logs directory exists
const logsDir = path.resolve(config.logging.filePath);
if (!fs.existsSync(logsDir)) {
  fs.mkdirSync(logsDir, { recursive: true });
}

// Custom format for development
const developmentFormat = winston.format.combine(
  winston.format.timestamp({ format: 'YYYY-MM-DD HH:mm:ss' }),
  winston.format.errors({ stack: true }),
  winston.format.colorize(),
  winston.format.printf((info) => {
    const { timestamp, level, message, ...meta } = info;
    const metaString = Object.keys(meta).length ? `\n${JSON.stringify(meta, null, 2)}` : '';
    return `${timestamp} [${level}]: ${message}${metaString}`;
  })
);

// Custom format for production
const productionFormat = winston.format.combine(
  winston.format.timestamp(),
  winston.format.errors({ stack: true }),
  winston.format.json(),
  winston.format.printf((info) => {
    const { level, message, timestamp, ...rest } = info;
    return JSON.stringify({
      timestamp,
      level,
      message,
      service: config.app.name,
      version: config.app.version,
      environment: config.app.env,
      ...rest,
    });
  })
);

// Create logger instance
export const logger = winston.createLogger({
  level: config.logging.level,
  format: isProduction() ? productionFormat : developmentFormat,
  defaultMeta: {
    service: config.app.name,
    environment: config.app.env,
  },
  transports: [],
  exitOnError: false,
});

// Console transport for all environments (needed for Docker logs)
logger.add(
  new winston.transports.Console({
    handleExceptions: true,
    handleRejections: true,
    format: isDevelopment() || isTest() ? developmentFormat : productionFormat,
  })
);

// File transports for production and staging
if (isProduction() || config.app.env === 'staging') {
  // Error logs
  logger.add(
    new winston.transports.File({
      filename: path.join(logsDir, 'error.log'),
      level: 'error',
      maxsize: parseSize(config.logging.maxSize),
      maxFiles: config.logging.maxFiles,
      handleExceptions: true,
      handleRejections: true,
      format: productionFormat,
    })
  );

  // Combined logs
  logger.add(
    new winston.transports.File({
      filename: path.join(logsDir, 'combined.log'),
      maxsize: parseSize(config.logging.maxSize),
      maxFiles: config.logging.maxFiles,
      format: productionFormat,
    })
  );

  // Application logs
  logger.add(
    new winston.transports.File({
      filename: path.join(logsDir, 'application.log'),
      level: 'info',
      maxsize: parseSize(config.logging.maxSize),
      maxFiles: config.logging.maxFiles,
      format: productionFormat,
    })
  );
}

// Helper function to parse size strings
function parseSize(size: string): number {
  const units: Record<string, number> = {
    b: 1,
    k: 1024,
    m: 1024 * 1024,
    g: 1024 * 1024 * 1024,
  };

  const match = size.toLowerCase().match(/^(\d+)([kmg]?)$/);
  if (!match) return 10 * 1024 * 1024; // Default 10MB

  const [, num, unit] = match;
  return parseInt(num || '10') * (units[unit || 'b'] || 1);
}

// Request logging middleware helper
export const createRequestLogger = () => {
  return (req: any, res: any, next: any) => {
    const start = Date.now();
    const requestId = req.headers['x-request-id'] || Math.random().toString(36).substr(2, 9);

    // Add request ID to request object
    req.requestId = requestId;

    // Log request
    logger.info('HTTP Request', {
      requestId,
      method: req.method,
      url: req.url,
      userAgent: req.get('user-agent'),
      ip: req.ip,
      timestamp: new Date().toISOString(),
    });

    // Override res.end to log response
    const originalEnd = res.end;
    res.end = function (chunk: any, encoding: any) {
      const duration = Date.now() - start;
      const contentLength = res.get('content-length') || 0;

      logger.info('HTTP Response', {
        requestId,
        method: req.method,
        url: req.url,
        statusCode: res.statusCode,
        contentLength,
        duration: `${duration}ms`,
        timestamp: new Date().toISOString(),
      });

      originalEnd.call(this, chunk, encoding);
    };

    next();
  };
};

// Error logging helper
export const logError = (error: Error, context?: Record<string, any>) => {
  logger.error('Application Error', {
    name: error.name,
    message: error.message,
    stack: error.stack,
    ...context,
    timestamp: new Date().toISOString(),
  });
};

// Database operation logging
export const logDatabaseOperation = (operation: string, details: Record<string, any>) => {
  logger.debug('Database Operation', {
    operation,
    ...details,
    timestamp: new Date().toISOString(),
  });
};

// Cache operation logging
export const logCacheOperation = (operation: string, key: string, hit?: boolean) => {
  logger.debug('Cache Operation', {
    operation,
    key,
    hit,
    timestamp: new Date().toISOString(),
  });
};

// Authentication logging
export const logAuthEvent = (event: string, userId?: string, details?: Record<string, any>) => {
  logger.info('Authentication Event', {
    event,
    userId,
    ...details,
    timestamp: new Date().toISOString(),
  });
};

// API performance logging
export const logApiPerformance = (
  endpoint: string,
  method: string,
  duration: number,
  statusCode: number
) => {
  const level = statusCode >= 400 ? 'warn' : 'info';
  const message = statusCode >= 400 ? 'API Error' : 'API Performance';

  logger[level](message, {
    endpoint,
    method,
    duration: `${duration}ms`,
    statusCode,
    timestamp: new Date().toISOString(),
  });
};

// Business logic logging
export const logBusinessEvent = (event: string, details: Record<string, any>) => {
  logger.info('Business Event', {
    event,
    ...details,
    timestamp: new Date().toISOString(),
  });
};

// Security event logging
export const logSecurityEvent = (event: string, severity: 'low' | 'medium' | 'high' | 'critical', details: Record<string, any>) => {
  const level = severity === 'critical' || severity === 'high' ? 'error' : 'warn';

  logger[level]('Security Event', {
    event,
    severity,
    ...details,
    timestamp: new Date().toISOString(),
  });
};

// Structured logging helpers
export const createLogger = (module: string) => {
  return {
    info: (message: string, meta?: Record<string, any>) => {
      logger.info(message, { module, ...meta });
    },
    warn: (message: string, meta?: Record<string, any>) => {
      logger.warn(message, { module, ...meta });
    },
    error: (message: string, meta?: Record<string, any>) => {
      logger.error(message, { module, ...meta });
    },
    debug: (message: string, meta?: Record<string, any>) => {
      logger.debug(message, { module, ...meta });
    },
  };
};

// Performance monitoring
export class PerformanceLogger {
  private startTime: number;
  private context: Record<string, any>;

  constructor(context: Record<string, any> = {}) {
    this.startTime = Date.now();
    this.context = context;
  }

  public mark(checkpoint: string): void {
    const duration = Date.now() - this.startTime;
    logger.debug('Performance Checkpoint', {
      checkpoint,
      duration: `${duration}ms`,
      ...this.context,
    });
  }

  public end(operation: string): void {
    const duration = Date.now() - this.startTime;
    logger.info('Performance Summary', {
      operation,
      totalDuration: `${duration}ms`,
      ...this.context,
    });
  }
}

// Log rotation and cleanup (if needed)
export const cleanupLogs = (daysToKeep: number = 30) => {
  const cutoffDate = new Date();
  cutoffDate.setDate(cutoffDate.getDate() - daysToKeep);

  try {
    const files = fs.readdirSync(logsDir);
    files.forEach(file => {
      const filePath = path.join(logsDir, file);
      const stats = fs.statSync(filePath);

      if (stats.mtime < cutoffDate) {
        fs.unlinkSync(filePath);
        logger.info(`Cleaned up old log file: ${file}`);
      }
    });
  } catch (error) {
    logger.error('Log cleanup failed', { error });
  }
};

// Export default logger and utilities
export default logger;