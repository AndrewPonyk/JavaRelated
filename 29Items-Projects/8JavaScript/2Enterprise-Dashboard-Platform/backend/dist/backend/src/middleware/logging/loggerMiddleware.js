import { logger, PerformanceLogger } from '@/utils/logger.js';
function generateRequestId() {
    return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
}
export const requestLogger = (req, res, next) => {
    const startTime = Date.now();
    const requestId = req.headers['x-request-id'] || generateRequestId();
    req.requestId = requestId;
    req.startTime = startTime;
    req.performanceLogger = new PerformanceLogger({
        requestId,
        method: req.method,
        url: req.url,
        userAgent: req.get('user-agent'),
        ip: req.ip,
    });
    res.setHeader('x-request-id', requestId);
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
    const originalEnd = res.end;
    res.end = function (chunk, encoding) {
        const duration = Date.now() - startTime;
        const contentLength = res.get('content-length') || 0;
        logger.info('HTTP Request Completed', {
            requestId,
            method: req.method,
            url: req.url,
            statusCode: res.statusCode,
            contentLength,
            duration: `${duration}ms`,
            timestamp: new Date().toISOString(),
        });
        req.performanceLogger.end(`${req.method} ${req.url}`);
        originalEnd.call(this, chunk, encoding);
    };
    const slowRequestThreshold = 5000;
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
    res.on('finish', () => {
        clearTimeout(slowRequestTimer);
    });
    next();
};
export const errorLogger = (error, req, res, next) => {
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
export const securityLogger = (eventType) => {
    return (req, res, next) => {
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
export const dbQueryLogger = (operation) => {
    return (req, res, next) => {
        const requestId = req.requestId || 'unknown';
        req.performanceLogger.mark(`db_${operation}_start`);
        const originalEnd = res.end;
        res.end = function (chunk, encoding) {
            req.performanceLogger.mark(`db_${operation}_end`);
            originalEnd.call(this, chunk, encoding);
        };
        next();
    };
};
export const endpointLogger = (endpoint) => {
    return (req, res, next) => {
        const requestId = req.requestId || 'unknown';
        logger.debug(`API Endpoint: ${endpoint}`, {
            requestId,
            method: req.method,
            endpoint,
            params: req.params,
            query: req.query,
            userId: req.user?.id,
            timestamp: new Date().toISOString(),
        });
        next();
    };
};
export const businessLogger = (action) => {
    return (req, res, next) => {
        const requestId = req.requestId || 'unknown';
        logger.info(`Business Action: ${action}`, {
            requestId,
            action,
            method: req.method,
            url: req.url,
            userId: req.user?.id,
            timestamp: new Date().toISOString(),
        });
        next();
    };
};
export const rateLimitLogger = (req, res, next) => {
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
export const auditLogger = (operation, resourceType) => {
    return (req, res, next) => {
        const requestId = req.requestId || 'unknown';
        const userId = req.user?.id;
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
        const originalEnd = res.end;
        res.end = function (chunk, encoding) {
            const success = res.statusCode >= 200 && res.statusCode < 300;
            logger.info('Audit Log - Operation Completed', {
                requestId,
                operation,
                resourceType,
                resourceId: req.params.id,
                userId,
                success,
                statusCode: res.statusCode,
                duration: req.startTime ? `${Date.now() - req.startTime}ms` : undefined,
                timestamp: new Date().toISOString(),
            });
            originalEnd.call(this, chunk, encoding);
        };
        next();
    };
};
export const createStructuredLogger = (context) => {
    return {
        info: (message, meta) => {
            logger.info(message, { ...context, ...meta });
        },
        warn: (message, meta) => {
            logger.warn(message, { ...context, ...meta });
        },
        error: (message, meta) => {
            logger.error(message, { ...context, ...meta });
        },
        debug: (message, meta) => {
            logger.debug(message, { ...context, ...meta });
        },
    };
};
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
//# sourceMappingURL=loggerMiddleware.js.map