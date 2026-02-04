import winston from 'winston';
import path from 'path';
import fs from 'fs';
import { config, isDevelopment, isProduction, isTest } from '@/config/environment.js';
const logsDir = path.resolve(config.logging.filePath);
if (!fs.existsSync(logsDir)) {
    fs.mkdirSync(logsDir, { recursive: true });
}
const developmentFormat = winston.format.combine(winston.format.timestamp({ format: 'YYYY-MM-DD HH:mm:ss' }), winston.format.errors({ stack: true }), winston.format.colorize(), winston.format.printf(({ timestamp, level, message, ...meta }) => {
    const metaString = Object.keys(meta).length ? `\n${JSON.stringify(meta, null, 2)}` : '';
    return `${timestamp} [${level}]: ${message}${metaString}`;
}));
const productionFormat = winston.format.combine(winston.format.timestamp(), winston.format.errors({ stack: true }), winston.format.json(), winston.format.printf((info) => {
    return JSON.stringify({
        timestamp: info.timestamp,
        level: info.level,
        message: info.message,
        service: config.app.name,
        version: config.app.version,
        environment: config.app.env,
        ...info,
    });
}));
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
if (isDevelopment() || isTest()) {
    logger.add(new winston.transports.Console({
        handleExceptions: true,
        handleRejections: true,
        format: developmentFormat,
    }));
}
if (isProduction() || config.app.env === 'staging') {
    logger.add(new winston.transports.File({
        filename: path.join(logsDir, 'error.log'),
        level: 'error',
        maxsize: parseSize(config.logging.maxSize),
        maxFiles: config.logging.maxFiles,
        handleExceptions: true,
        handleRejections: true,
        format: productionFormat,
    }));
    logger.add(new winston.transports.File({
        filename: path.join(logsDir, 'combined.log'),
        maxsize: parseSize(config.logging.maxSize),
        maxFiles: config.logging.maxFiles,
        format: productionFormat,
    }));
    logger.add(new winston.transports.File({
        filename: path.join(logsDir, 'application.log'),
        level: 'info',
        maxsize: parseSize(config.logging.maxSize),
        maxFiles: config.logging.maxFiles,
        format: productionFormat,
    }));
}
function parseSize(size) {
    const units = {
        b: 1,
        k: 1024,
        m: 1024 * 1024,
        g: 1024 * 1024 * 1024,
    };
    const match = size.toLowerCase().match(/^(\d+)([kmg]?)$/);
    if (!match)
        return 10 * 1024 * 1024;
    const [, num, unit] = match;
    return parseInt(num) * (units[unit || 'b'] || 1);
}
export const createRequestLogger = () => {
    return (req, res, next) => {
        const start = Date.now();
        const requestId = req.headers['x-request-id'] || Math.random().toString(36).substr(2, 9);
        req.requestId = requestId;
        logger.info('HTTP Request', {
            requestId,
            method: req.method,
            url: req.url,
            userAgent: req.get('user-agent'),
            ip: req.ip,
            timestamp: new Date().toISOString(),
        });
        const originalEnd = res.end;
        res.end = function (chunk, encoding) {
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
export const logError = (error, context) => {
    logger.error('Application Error', {
        name: error.name,
        message: error.message,
        stack: error.stack,
        ...context,
        timestamp: new Date().toISOString(),
    });
};
export const logDatabaseOperation = (operation, details) => {
    logger.debug('Database Operation', {
        operation,
        ...details,
        timestamp: new Date().toISOString(),
    });
};
export const logCacheOperation = (operation, key, hit) => {
    logger.debug('Cache Operation', {
        operation,
        key,
        hit,
        timestamp: new Date().toISOString(),
    });
};
export const logAuthEvent = (event, userId, details) => {
    logger.info('Authentication Event', {
        event,
        userId,
        ...details,
        timestamp: new Date().toISOString(),
    });
};
export const logApiPerformance = (endpoint, method, duration, statusCode) => {
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
export const logBusinessEvent = (event, details) => {
    logger.info('Business Event', {
        event,
        ...details,
        timestamp: new Date().toISOString(),
    });
};
export const logSecurityEvent = (event, severity, details) => {
    const level = severity === 'critical' || severity === 'high' ? 'error' : 'warn';
    logger[level]('Security Event', {
        event,
        severity,
        ...details,
        timestamp: new Date().toISOString(),
    });
};
export const createLogger = (module) => {
    return {
        info: (message, meta) => {
            logger.info(message, { module, ...meta });
        },
        warn: (message, meta) => {
            logger.warn(message, { module, ...meta });
        },
        error: (message, meta) => {
            logger.error(message, { module, ...meta });
        },
        debug: (message, meta) => {
            logger.debug(message, { module, ...meta });
        },
    };
};
export class PerformanceLogger {
    startTime;
    context;
    constructor(context = {}) {
        this.startTime = Date.now();
        this.context = context;
    }
    mark(checkpoint) {
        const duration = Date.now() - this.startTime;
        logger.debug('Performance Checkpoint', {
            checkpoint,
            duration: `${duration}ms`,
            ...this.context,
        });
    }
    end(operation) {
        const duration = Date.now() - this.startTime;
        logger.info('Performance Summary', {
            operation,
            totalDuration: `${duration}ms`,
            ...this.context,
        });
    }
}
export const cleanupLogs = (daysToKeep = 30) => {
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
    }
    catch (error) {
        logger.error('Log cleanup failed', { error });
    }
};
export default logger;
//# sourceMappingURL=logger.js.map