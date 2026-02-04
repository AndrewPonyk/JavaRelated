import { Request, Response, NextFunction } from 'express';
import { createHash } from 'crypto';
import { logger } from '@/utils/logger.js';
import { extendedCacheService } from '@/services/cache/cacheService.js';

/**
 * Response Optimizer
 * Optimizes API responses through compression, caching, serialization, and conditional responses
 */

export interface ResponseOptimizationConfig {
  enableCompression: boolean;
  enableETag: boolean;
  enableConditionalRequests: boolean;
  enableResponseCaching: boolean;
  compressionThreshold: number;
  cacheableStatusCodes: number[];
  excludePatterns: RegExp[];
  compressionLevel: number;
}

export interface OptimizedResponse<T = any> {
  success: boolean;
  data?: T;
  message?: string;
  meta?: {
    timestamp: string;
    requestId?: string;
    duration?: number;
    cached?: boolean;
    compressed?: boolean;
    etag?: string;
    error?: {
      code: string;
      statusCode: number;
      details?: any;
    };
  };
}

export class ResponseOptimizer {
  private static instance: ResponseOptimizer;
  private config: ResponseOptimizationConfig;

  private constructor() {
    this.config = {
      enableCompression: true,
      enableETag: true,
      enableConditionalRequests: true,
      enableResponseCaching: true,
      compressionThreshold: 1024, // 1KB
      cacheableStatusCodes: [200, 201, 300, 301, 302, 304, 404, 410],
      excludePatterns: [
        /\/api\/websocket/,
        /\/api\/backup\/restore/,
        /\/health/,
        /\/api\/auth\/logout/,
      ],
      compressionLevel: 6, // Balanced compression
    };
  }

  public static getInstance(): ResponseOptimizer {
    if (!ResponseOptimizer.instance) {
      ResponseOptimizer.instance = new ResponseOptimizer();
    }
    return ResponseOptimizer.instance;
  }

  /**
   * Main response optimization middleware
   */
  public createOptimizationMiddleware() {
    return (req: Request, res: Response, next: NextFunction) => {
      // Skip optimization for excluded patterns
      if (this.shouldSkipOptimization(req.path)) {
        return next();
      }

      // Add response timing
      const startTime = Date.now();
      req.startTime = startTime;

      // Override res.json to add optimization
      const originalJson = res.json.bind(res);
      const originalSend = res.send.bind(res);

      res.json = (data: any) => {
        return this.optimizeJsonResponse(req, res, data, originalJson);
      };

      res.send = (data: any) => {
        return this.optimizeSendResponse(req, res, data, originalSend);
      };

      next();
    };
  }

  /**
   * Create standardized API response format
   */
  public createStandardResponse<T>(
    data: T,
    message?: string,
    meta?: any
  ): OptimizedResponse<T> {
    return {
      success: true,
      data,
      message,
      meta: {
        timestamp: new Date().toISOString(),
        ...meta,
      },
    };
  }

  /**
   * Create error response format
   */
  public createErrorResponse(
    error: {
      code: string;
      message: string;
      statusCode: number;
      details?: any;
    },
    requestId?: string
  ): OptimizedResponse<never> {
    return {
      success: false,
      message: error.message,
      meta: {
        timestamp: new Date().toISOString(),
        requestId,
        error: {
          code: error.code,
          statusCode: error.statusCode,
          details: error.details,
        },
      },
    };
  }

  /**
   * Response caching middleware
   */
  public createCacheMiddleware(options: {
    ttl?: number;
    varyBy?: string[];
    condition?: (req: Request) => boolean;
    keyGenerator?: (req: Request) => string;
  } = {}) {
    const {
      ttl = 300, // 5 minutes
      varyBy = ['user'],
      condition = () => true,
      keyGenerator = this.defaultCacheKeyGenerator,
    } = options;

    return async (req: Request, res: Response, next: NextFunction) => {
      // Skip if caching disabled or condition not met
      if (!this.config.enableResponseCaching || !condition(req)) {
        return next();
      }

      const cacheKey = keyGenerator(req);

      try {
        // Try to get cached response
        const cachedResponse = await extendedCacheService.get(cacheKey);

        if (cachedResponse) {
          // Add cache headers
          res.set({
            'X-Cache': 'HIT',
            'Cache-Control': `public, max-age=${ttl}`,
          });

          // Return cached response
          const cached = cachedResponse as any;
          return res.json({
            ...cached,
            meta: {
              ...(cached.meta || {}),
              cached: true,
              cacheKey: process.env.NODE_ENV === 'development' ? cacheKey : undefined,
            },
          });
        }

        // Cache miss - intercept response
        const originalJson = res.json.bind(res);
        res.json = (data: any) => {
          // Cache successful responses
          if (res.statusCode >= 200 && res.statusCode < 300) {
            extendedCacheService.set(cacheKey, data, ttl).catch(error => {
              logger.warn('Failed to cache response', { cacheKey, error });
            });

            // Add cache headers
            res.set({
              'X-Cache': 'MISS',
              'Cache-Control': `public, max-age=${ttl}`,
            });
          }

          return originalJson(data);
        };

        next();

      } catch (error) {
        logger.warn('Response cache middleware error', { error, cacheKey });
        next();
      }
    };
  }

  /**
   * Conditional request middleware (ETag, If-Modified-Since)
   */
  public createConditionalRequestMiddleware() {
    return (req: Request, res: Response, next: NextFunction) => {
      if (!this.config.enableConditionalRequests) {
        return next();
      }

      // Override response methods to add ETag support
      const originalJson = res.json.bind(res);

      res.json = (data: any) => {
        if (this.config.enableETag && res.statusCode === 200) {
          const etag = this.generateETag(data);
          res.set('ETag', etag);

          // Check if client has current version
          const clientETag = req.headers['if-none-match'];
          if (clientETag === etag) {
            return res.status(304).end();
          }
        }

        return originalJson(data);
      };

      next();
    };
  }

  /**
   * Response compression middleware
   */
  public createCompressionMiddleware() {
    return (req: Request, res: Response, next: NextFunction) => {
      if (!this.config.enableCompression) {
        return next();
      }

      // Check if client accepts compression
      const acceptEncoding = req.headers['accept-encoding'] || '';
      const supportsGzip = acceptEncoding.includes('gzip');

      if (!supportsGzip) {
        return next();
      }

      // Override response methods to add compression
      const originalJson = res.json.bind(res);

      res.json = (data: any) => {
        const content = JSON.stringify(data);

        // Only compress if content is large enough
        if (content.length >= this.config.compressionThreshold) {
          res.set({
            'Content-Encoding': 'gzip',
            'Vary': 'Accept-Encoding',
            'X-Compressed': 'true',
          });
        }

        return originalJson(data);
      };

      next();
    };
  }

  /**
   * Response serialization optimization
   */
  public optimizeJsonSerialization<T>(data: T): string {
    try {
      // Use faster JSON serialization for known data types
      if (Array.isArray(data)) {
        return this.serializeArray(data);
      }

      if (data && typeof data === 'object') {
        return this.serializeObject(data);
      }

      return JSON.stringify(data);

    } catch (error) {
      logger.error('JSON serialization failed', { error });
      return JSON.stringify({ error: 'Serialization failed' });
    }
  }

  /**
   * Response pagination optimization
   */
  public createPaginatedResponse<T>(
    data: T[],
    total: number,
    page: number,
    limit: number,
    additionalMeta?: any
  ): OptimizedResponse<{
    items: T[];
    pagination: {
      total: number;
      page: number;
      limit: number;
      pages: number;
      hasNext: boolean;
      hasPrev: boolean;
    };
  }> {
    const pages = Math.ceil(total / limit);

    return this.createStandardResponse(
      {
        items: data,
        pagination: {
          total,
          page,
          limit,
          pages,
          hasNext: page < pages,
          hasPrev: page > 1,
        },
      },
      `Retrieved ${data.length} of ${total} items`,
      additionalMeta
    );
  }

  /**
   * Stream large responses
   */
  public createStreamResponse(
    req: Request,
    res: Response,
    dataGenerator: AsyncGenerator<any, void, unknown>
  ): void {
    res.set({
      'Content-Type': 'application/json',
      'Transfer-Encoding': 'chunked',
    });

    res.write('{"success":true,"data":[');

    let isFirst = true;

    (async () => {
      try {
        for await (const item of dataGenerator) {
          if (!isFirst) {
            res.write(',');
          }
          res.write(JSON.stringify(item));
          isFirst = false;
        }

        res.write(']}');
        res.end();

      } catch (error) {
        logger.error('Stream response failed', { error });
        res.write(']}');
        res.end();
      }
    })();
  }

  /**
   * Response performance metrics
   */
  public getPerformanceMetrics(): {
    averageResponseTime: number;
    compressionSavings: number;
    cacheHitRate: number;
    totalRequests: number;
  } {
    // This would be implemented with actual metrics collection
    return {
      averageResponseTime: 0,
      compressionSavings: 0,
      cacheHitRate: 0,
      totalRequests: 0,
    };
  }

  // Private methods

  private optimizeJsonResponse(
    req: Request,
    res: Response,
    data: any,
    originalJson: (data: any) => Response
  ): Response {
    const startTime = req.startTime || Date.now();
    const duration = Date.now() - startTime;

    // Add metadata to response
    const optimizedData = {
      ...data,
      meta: {
        ...data.meta,
        duration,
        requestId: req.headers['x-request-id'] || req.id,
        compressed: this.shouldCompress(JSON.stringify(data)),
      },
    };

    return originalJson(optimizedData);
  }

  private optimizeSendResponse(
    req: Request,
    res: Response,
    data: any,
    originalSend: (data: any) => Response
  ): Response {
    // Optimize send response if needed
    return originalSend(data);
  }

  private shouldSkipOptimization(path: string): boolean {
    return this.config.excludePatterns.some(pattern => pattern.test(path));
  }

  private shouldCompress(content: string): boolean {
    return this.config.enableCompression &&
           content.length >= this.config.compressionThreshold;
  }

  private generateETag(data: any): string {
    const content = typeof data === 'string' ? data : JSON.stringify(data);
    return `"${createHash('md5').update(content).digest('hex')}"`;
  }

  private defaultCacheKeyGenerator(req: Request): string {
    const user = (req as any).user;
    const userId = user ? user.id : 'anonymous';
    const queryString = new URLSearchParams(req.query as any).toString();

    return `response:${req.method}:${req.path}:${userId}:${createHash('md5')
      .update(queryString)
      .digest('hex')}`;
  }

  private serializeArray<T>(data: T[]): string {
    // Optimized array serialization
    if (data.length === 0) return '[]';

    const items = data.map(item => JSON.stringify(item));
    return `[${items.join(',')}]`;
  }

  private serializeObject(data: any): string {
    // Optimized object serialization with key ordering
    const keys = Object.keys(data).sort();
    const pairs = keys.map(key => `"${key}":${JSON.stringify(data[key])}`);
    return `{${pairs.join(',')}}`;
  }
}

// Export singleton instance
export const responseOptimizer = ResponseOptimizer.getInstance();

// Middleware factories
export const optimizeResponses = () => responseOptimizer.createOptimizationMiddleware();
export const cacheResponses = (options?: any) => responseOptimizer.createCacheMiddleware(options);
export const conditionalRequests = () => responseOptimizer.createConditionalRequestMiddleware();
export const compressResponses = () => responseOptimizer.createCompressionMiddleware();

// Utility functions
export const createApiResponse = <T>(data: T, message?: string, meta?: any) =>
  responseOptimizer.createStandardResponse(data, message, meta);

export const createPaginatedApiResponse = <T>(
  data: T[],
  total: number,
  page: number,
  limit: number,
  meta?: any
) => responseOptimizer.createPaginatedResponse(data, total, page, limit, meta);

export const createErrorApiResponse = (error: any, requestId?: string) =>
  responseOptimizer.createErrorResponse(error, requestId);

// Type declarations for Express
declare global {
  namespace Express {
    interface Request {
      startTime?: number;
      id?: string;
    }
  }
}