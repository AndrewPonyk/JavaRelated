import { createHash } from 'crypto';
import { logger } from '@/utils/logger.js';
import { extendedCacheService } from '@/services/cache/cacheService.js';
export class ResponseOptimizer {
    static instance;
    config;
    constructor() {
        this.config = {
            enableCompression: true,
            enableETag: true,
            enableConditionalRequests: true,
            enableResponseCaching: true,
            compressionThreshold: 1024,
            cacheableStatusCodes: [200, 201, 300, 301, 302, 304, 404, 410],
            excludePatterns: [
                /\/api\/websocket/,
                /\/api\/backup\/restore/,
                /\/health/,
                /\/api\/auth\/logout/,
            ],
            compressionLevel: 6,
        };
    }
    static getInstance() {
        if (!ResponseOptimizer.instance) {
            ResponseOptimizer.instance = new ResponseOptimizer();
        }
        return ResponseOptimizer.instance;
    }
    createOptimizationMiddleware() {
        return (req, res, next) => {
            if (this.shouldSkipOptimization(req.path)) {
                return next();
            }
            const startTime = Date.now();
            req.startTime = startTime;
            const originalJson = res.json.bind(res);
            const originalSend = res.send.bind(res);
            res.json = (data) => {
                return this.optimizeJsonResponse(req, res, data, originalJson);
            };
            res.send = (data) => {
                return this.optimizeSendResponse(req, res, data, originalSend);
            };
            next();
        };
    }
    createStandardResponse(data, message, meta) {
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
    createErrorResponse(error, requestId) {
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
    createCacheMiddleware(options = {}) {
        const { ttl = 300, varyBy = ['user'], condition = () => true, keyGenerator = this.defaultCacheKeyGenerator, } = options;
        return async (req, res, next) => {
            if (!this.config.enableResponseCaching || !condition(req)) {
                return next();
            }
            const cacheKey = keyGenerator(req);
            try {
                const cachedResponse = await extendedCacheService.get(cacheKey);
                if (cachedResponse) {
                    res.set({
                        'X-Cache': 'HIT',
                        'Cache-Control': `public, max-age=${ttl}`,
                    });
                    return res.json({
                        ...cachedResponse,
                        meta: {
                            ...cachedResponse.meta,
                            cached: true,
                            cacheKey: process.env.NODE_ENV === 'development' ? cacheKey : undefined,
                        },
                    });
                }
                const originalJson = res.json.bind(res);
                res.json = (data) => {
                    if (res.statusCode >= 200 && res.statusCode < 300) {
                        extendedCacheService.set(cacheKey, data, ttl).catch(error => {
                            logger.warn('Failed to cache response', { cacheKey, error });
                        });
                        res.set({
                            'X-Cache': 'MISS',
                            'Cache-Control': `public, max-age=${ttl}`,
                        });
                    }
                    return originalJson(data);
                };
                next();
            }
            catch (error) {
                logger.warn('Response cache middleware error', { error, cacheKey });
                next();
            }
        };
    }
    createConditionalRequestMiddleware() {
        return (req, res, next) => {
            if (!this.config.enableConditionalRequests) {
                return next();
            }
            const originalJson = res.json.bind(res);
            res.json = (data) => {
                if (this.config.enableETag && res.statusCode === 200) {
                    const etag = this.generateETag(data);
                    res.set('ETag', etag);
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
    createCompressionMiddleware() {
        return (req, res, next) => {
            if (!this.config.enableCompression) {
                return next();
            }
            const acceptEncoding = req.headers['accept-encoding'] || '';
            const supportsGzip = acceptEncoding.includes('gzip');
            if (!supportsGzip) {
                return next();
            }
            const originalJson = res.json.bind(res);
            res.json = (data) => {
                const content = JSON.stringify(data);
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
    optimizeJsonSerialization(data) {
        try {
            if (Array.isArray(data)) {
                return this.serializeArray(data);
            }
            if (data && typeof data === 'object') {
                return this.serializeObject(data);
            }
            return JSON.stringify(data);
        }
        catch (error) {
            logger.error('JSON serialization failed', { error });
            return JSON.stringify({ error: 'Serialization failed' });
        }
    }
    createPaginatedResponse(data, total, page, limit, additionalMeta) {
        const pages = Math.ceil(total / limit);
        return this.createStandardResponse({
            items: data,
            pagination: {
                total,
                page,
                limit,
                pages,
                hasNext: page < pages,
                hasPrev: page > 1,
            },
        }, `Retrieved ${data.length} of ${total} items`, additionalMeta);
    }
    createStreamResponse(req, res, dataGenerator) {
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
            }
            catch (error) {
                logger.error('Stream response failed', { error });
                res.write(']}');
                res.end();
            }
        })();
    }
    getPerformanceMetrics() {
        return {
            averageResponseTime: 0,
            compressionSavings: 0,
            cacheHitRate: 0,
            totalRequests: 0,
        };
    }
    optimizeJsonResponse(req, res, data, originalJson) {
        const startTime = req.startTime || Date.now();
        const duration = Date.now() - startTime;
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
    optimizeSendResponse(req, res, data, originalSend) {
        return originalSend(data);
    }
    shouldSkipOptimization(path) {
        return this.config.excludePatterns.some(pattern => pattern.test(path));
    }
    shouldCompress(content) {
        return this.config.enableCompression &&
            content.length >= this.config.compressionThreshold;
    }
    generateETag(data) {
        const content = typeof data === 'string' ? data : JSON.stringify(data);
        return `"${createHash('md5').update(content).digest('hex')}"`;
    }
    defaultCacheKeyGenerator(req) {
        const user = req.user;
        const userId = user ? user.id : 'anonymous';
        const queryString = new URLSearchParams(req.query).toString();
        return `response:${req.method}:${req.path}:${userId}:${createHash('md5')
            .update(queryString)
            .digest('hex')}`;
    }
    serializeArray(data) {
        if (data.length === 0)
            return '[]';
        const items = data.map(item => JSON.stringify(item));
        return `[${items.join(',')}]`;
    }
    serializeObject(data) {
        const keys = Object.keys(data).sort();
        const pairs = keys.map(key => `"${key}":${JSON.stringify(data[key])}`);
        return `{${pairs.join(',')}}`;
    }
}
export const responseOptimizer = ResponseOptimizer.getInstance();
export const optimizeResponses = () => responseOptimizer.createOptimizationMiddleware();
export const cacheResponses = (options) => responseOptimizer.createCacheMiddleware(options);
export const conditionalRequests = () => responseOptimizer.createConditionalRequestMiddleware();
export const compressResponses = () => responseOptimizer.createCompressionMiddleware();
export const createApiResponse = (data, message, meta) => responseOptimizer.createStandardResponse(data, message, meta);
export const createPaginatedApiResponse = (data, total, page, limit, meta) => responseOptimizer.createPaginatedResponse(data, total, page, limit, meta);
export const createErrorApiResponse = (error, requestId) => responseOptimizer.createErrorResponse(error, requestId);
//# sourceMappingURL=responseOptimizer.js.map