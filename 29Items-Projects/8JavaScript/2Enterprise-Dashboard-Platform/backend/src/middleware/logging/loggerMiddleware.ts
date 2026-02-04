import { Request, Response, NextFunction } from 'express';
import { logger, PerformanceLogger } from '@/utils/logger.js';
import { config } from '@/config/environment.js';

// Extended Request interface for logging context
interface LoggedRequest extends Request {
  requestId: string;
  startTime: number;
  performanceLogger: PerformanceLogger;
}

// Generate unique request ID
function generateRequestId(): string {
  return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
}

// Main request logging middleware
export const requestLogger = (req: LoggedRequest, res: Response, next: NextFunction): void => {
  const startTime = Date.now();
  const requestId = req.headers['x-request-id'] as string || generateRequestId();

  // Add context to request
  req.requestId = requestId;
  req.startTime = startTime;
  req.performanceLogger = new PerformanceLogger({
    requestId,
    method: req.method,
    url: req.url,
    userAgent: req.get('user-agent'),
    ip: req.ip,
  });

  // Set response header
  res.setHeader('x-request-id', requestId);

  // Log incoming request
  logger.info('HTTP Request Started', {
    requestId,
    method: req.method,
    url: req.url,
    userAgent: req.get('user-agent'),
    ip: req.ip,
    contentLength: req.get('content-length'),
    referer: req.get('referer'),
    timestamp: new Date().toISOString(),
  });

  // Override res.end to log response
  const originalEnd = res.end.bind(res);
  (res as any).end = function (chunk?: any, encoding?: any) {
    const duration = Date.now() - startTime;
    const contentLength = res.get('content-length') || 0;

    // Log response
    logger.info('HTTP Request Completed', {
      requestId,
      method: req.method,
      url: req.url,
      statusCode: res.statusCode,
      contentLength,
      duration: `${duration}ms`,
      timestamp: new Date().toISOString(),
    });

    // Log performance summary
    (req as any).performanceLogger?.end(`${req.method} ${req.url}`);

    // Call original end method
    return originalEnd(chunk, encoding);
  };

  // Log slow requests
  const slowRequestThreshold = 5000; // 5 seconds
  const slowRequestTimer = setTimeout(() => {
    logger.warn('Slow Request Detected', {
      requestId,
      method: req.method,
      url: req.url,
      duration: `${Date.now() - startTime}ms`,
      userAgent: req.get('user-agent'),
      ip: req.ip,
    });
  }, slowRequestThreshold);

  // Clear slow request timer when response completes
  res.on('finish', () => {
    clearTimeout(slowRequestTimer);
  });

  next();
};

// Error logging middleware
export const errorLogger = (error: Error, req: LoggedRequest, res: Response, next: NextFunction): void => {
  const requestId = req.requestId || 'unknown';
  const duration = req.startTime ? Date.now() - req.startTime : 0;

  logger.error('HTTP Request Error', {
    requestId,
    method: req.method,
    url: req.url,
    statusCode: res.statusCode || 500,
    duration: `${duration}ms`,
    error: {
      name: error.name,
      message: error.message,
      stack: error.stack,
    },
    userAgent: req.get('user-agent'),
    ip: req.ip,
    timestamp: new Date().toISOString(),
  });

  next(error);
};

// Security event logging middleware
export const securityLogger = (eventType: string) => {
  return (req: LoggedRequest, res: Response, next: NextFunction): void => {
    const requestId = req.requestId || 'unknown';

    logger.warn('Security Event', {
      eventType,
      requestId,
      method: req.method,
      url: req.url,
      userAgent: req.get('user-agent'),
      ip: req.ip,
      headers: {
        authorization: req.get('authorization') ? '[REDACTED]' : undefined,
        contentType: req.get('content-type'),
        origin: req.get('origin'),
        referer: req.get('referer'),
      },
      timestamp: new Date().toISOString(),
    });

    next();
  };
};

// Database query logging middleware
export const dbQueryLogger = (operation: string) => {
  return (req: LoggedRequest, res: Response, next: NextFunction): void => {
    const requestId = req.requestId || 'unknown';

    // Add database operation context
    req.performanceLogger.mark(`db_${operation}_start`);

    // Override res.end to mark completion
    const originalEnd = res.end.bind(res);
    (res as any).end = function (chunk?: any, encoding?: any) {
      (req as any).performanceLogger?.mark(`db_${operation}_end`);
      return originalEnd(chunk, encoding);
    };

    next();
  };
};

// API endpoint specific logging
export const endpointLogger = (endpoint: string) => {
  return (req: LoggedRequest, res: Response, next: NextFunction): void => {
    const requestId = req.requestId || 'unknown';

    logger.debug(`API Endpoint: ${endpoint}`, {
      requestId,
      method: req.method,
      endpoint,
      params: req.params,
      query: req.query,
      userId: (req as any).user?.id,
      timestamp: new Date().toISOString(),
    });

    next();
  };
};

// Business logic logging middleware
export const businessLogger = (action: string) => {
  return (req: LoggedRequest, res: Response, next: NextFunction): void => {
    const requestId = req.requestId || 'unknown';

    logger.info(`Business Action: ${action}`, {
      requestId,
      action,
      method: req.method,
      url: req.url,
      userId: (req as any).user?.id,
      timestamp: new Date().toISOString(),
    });

    next();
  };
};

// Rate limiting logging
export const rateLimitLogger = (req: LoggedRequest, res: Response, next: NextFunction): void => {
  const requestId = req.requestId || 'unknown';
  const remainingRequests = res.get('x-ratelimit-remaining');
  const resetTime = res.get('x-ratelimit-reset');

  if (remainingRequests && parseInt(remainingRequests) < 10) {
    logger.warn('Rate Limit Warning', {
      requestId,
      ip: req.ip,
      remainingRequests: parseInt(remainingRequests),
      resetTime,
      userAgent: req.get('user-agent'),
      timestamp: new Date().toISOString(),
    });
  }

  next();
};

// Audit logging for sensitive operations
export const auditLogger = (operation: string, resourceType?: string) => {
  return (req: LoggedRequest, res: Response, next: NextFunction): void => {
    const requestId = req.requestId || 'unknown';
    const userId = (req as any).user?.id;

    // Log before operation
    logger.info('Audit Log - Operation Started', {
      requestId,
      operation,
      resourceType,
      resourceId: req.params.id,
      userId,
      method: req.method,
      url: req.url,
      ip: req.ip,
      userAgent: req.get('user-agent'),
      timestamp: new Date().toISOString(),
    });

    // Override res.end to log completion
    const originalEnd = res.end.bind(res);
    (res as any).end = function (chunk?: any, encoding?: any) {
      const success = res.statusCode >= 200 && res.statusCode < 300;

      logger.info('Audit Log - Operation Completed', {
        requestId,
        operation,
        resourceType,
        resourceId: req.params.id,
        userId,
        success,
        statusCode: res.statusCode,
        duration: (req as any).startTime ? `${Date.now() - (req as any).startTime}ms` : undefined,
        timestamp: new Date().toISOString(),
      });

      return originalEnd(chunk, encoding);
    };

    next();
  };
};

// Structured logging helper for consistent log format
export const createStructuredLogger = (context: Record<string, any>) => {
  return {
    info: (message: string, meta?: Record<string, any>) => {
      logger.info(message, { ...context, ...meta });
    },
    warn: (message: string, meta?: Record<string, any>) => {
      logger.warn(message, { ...context, ...meta });
    },
    error: (message: string, meta?: Record<string, any>) => {
      logger.error(message, { ...context, ...meta });
    },
    debug: (message: string, meta?: Record<string, any>) => {
      logger.debug(message, { ...context, ...meta });
    },
  };
};

// Export all middleware functions
export default {
  requestLogger,
  errorLogger,
  securityLogger,
  dbQueryLogger,
  endpointLogger,
  businessLogger,
  rateLimitLogger,
  auditLogger,
  createStructuredLogger,
};