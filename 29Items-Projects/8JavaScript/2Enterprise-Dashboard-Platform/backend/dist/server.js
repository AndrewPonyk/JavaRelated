import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import compression from 'compression';
import cookieParser from 'cookie-parser';
import rateLimit from 'express-rate-limit';
import swaggerUi from 'swagger-ui-express';
import 'express-async-errors';
import { swaggerSpec } from '@/config/swagger.js';
import { config, isDevelopment, isProduction } from '@/config/environment.js';
import { initializeDatabase } from '@/config/database.js';
import { initializeRedis } from '@/config/redis.js';
import { logger, createRequestLogger } from '@/utils/logger.js';
import { errorHandler, notFoundHandler, gracefulShutdown } from '@/utils/errors.js';
import { createDataLoaderMiddleware } from '@/utils/dataLoader.js';
import { initializePerformanceOptimization, createOptimizedMiddleware, handlePerformanceMetricsRequest, handlePerformanceOptimizationRequest } from '@/utils/performance/index.js';
import authRoutes from '@/routes/auth.js';
import userRoutes from '@/routes/users.js';
import dashboardRoutes from '@/routes/dashboard.js';
import websocketRoutes from '@/routes/websocket.js';
import backupRoutes from '@/routes/backup.js';
import { websocketServer } from '@/services/websocket/websocketServer.js';
import { backupScheduler } from '@/services/backup/backupScheduler.js';
const app = express();
if (config.security.trustProxy) {
    app.set('trust proxy', 1);
}
if (config.security.enableHelmet) {
    app.use(helmet({
        contentSecurityPolicy: isDevelopment() ? false : undefined,
        crossOriginEmbedderPolicy: false,
    }));
}
if (config.security.enableCors) {
    app.use(cors({
        origin: (origin, callback) => {
            if (!origin)
                return callback(null, true);
            if (config.cors.origin.includes(origin)) {
                return callback(null, true);
            }
            const error = new Error('Not allowed by CORS');
            callback(error, false);
        },
        methods: config.cors.methods,
        allowedHeaders: config.cors.headers,
        credentials: true,
        maxAge: 86400,
    }));
}
if (config.security.enableRateLimit) {
    const globalRateLimit = rateLimit({
        windowMs: config.security.rateLimitWindowMs,
        max: config.security.rateLimitMaxRequests,
        message: {
            success: false,
            error: {
                code: 'RATE_LIMIT_EXCEEDED',
                message: 'Too many requests from this IP, please try again later',
                statusCode: 429,
            },
        },
        standardHeaders: true,
        legacyHeaders: false,
        skip: (req) => isDevelopment(),
    });
    app.use(globalRateLimit);
}
app.use(express.json({
    limit: '10mb',
    verify: (req, res, buf) => {
        req.rawBody = buf;
    },
}));
app.use(express.urlencoded({
    extended: true,
    limit: '10mb',
}));
app.use(cookieParser());
app.use(compression({
    level: 6,
    threshold: 1024,
    filter: (req, res) => {
        if (req.headers['x-no-compression']) {
            return false;
        }
        return compression.filter(req, res);
    },
}));
if (!isProduction()) {
    app.use(createRequestLogger());
}
app.use(createDataLoaderMiddleware());
const performanceMiddleware = createOptimizedMiddleware();
performanceMiddleware.forEach(middleware => app.use(middleware));
app.get('/health', async (req, res) => {
    try {
        const dbHealthy = config.healthCheck.database ?
            await import('@/config/database.js').then(db => db.dbManager.healthCheck()) :
            true;
        const redisHealthy = config.healthCheck.redis ?
            await import('@/config/redis.js').then(redis => redis.redisManager.healthCheck()) :
            true;
        const health = {
            status: (dbHealthy && redisHealthy) ? 'healthy' : 'unhealthy',
            timestamp: new Date().toISOString(),
            uptime: process.uptime(),
            environment: config.app.env,
            version: config.app.version,
            services: {
                database: dbHealthy ? 'healthy' : 'unhealthy',
                redis: redisHealthy ? 'healthy' : 'unhealthy',
            },
            memory: process.memoryUsage(),
            pid: process.pid,
        };
        const statusCode = health.status === 'healthy' ? 200 : 503;
        res.status(statusCode).json({
            success: health.status === 'healthy',
            data: health,
            message: `Service is ${health.status}`,
        });
    }
    catch (error) {
        logger.error('Health check failed', { error });
        res.status(503).json({
            success: false,
            data: {
                status: 'unhealthy',
                timestamp: new Date().toISOString(),
                error: error instanceof Error ? error.message : 'Unknown error',
            },
            message: 'Service is unhealthy',
        });
    }
});
if (isDevelopment() || config.app.env === 'staging') {
    app.use('/api/docs', swaggerUi.serve, swaggerUi.setup(swaggerSpec, {
        explorer: true,
        customSiteTitle: 'Enterprise Dashboard Platform API Docs',
        customfavIcon: '/favicon.ico',
        customCss: `
      .swagger-ui .topbar { display: none; }
      .swagger-ui .info { margin: 50px 0; }
      .swagger-ui .info .title { color: #3b82f6; }
    `,
        swaggerOptions: {
            persistAuthorization: true,
            displayRequestDuration: true,
            filter: true,
            showExtensions: true,
            showCommonExtensions: true,
        },
    }));
    app.get('/api/docs.json', (req, res) => {
        res.setHeader('Content-Type', 'application/json');
        res.send(swaggerSpec);
    });
    logger.info('📖 API Documentation available at /api/docs');
}
app.get('/api/performance/metrics', (req, res, next) => {
    handlePerformanceMetricsRequest(req, res).catch(next);
});
app.post('/api/performance/optimize', (req, res, next) => {
    handlePerformanceOptimizationRequest(req, res).catch(next);
});
app.use('/api/auth', authRoutes);
app.use('/api/users', userRoutes);
app.use('/api/dashboards', dashboardRoutes);
app.use('/api/websocket', websocketRoutes);
app.use('/api/backup', backupRoutes);
if (isDevelopment() && config.development.enableApiDocs) {
    app.get('/api', (req, res) => {
        res.json({
            success: true,
            data: {
                name: config.app.name,
                version: config.app.version,
                environment: config.app.env,
                endpoints: {
                    auth: '/api/auth',
                    users: '/api/users',
                    dashboards: '/api/dashboards',
                    health: '/health',
                },
                documentation: {
                    swagger: '/api/docs',
                    postman: '/api/postman',
                },
            },
            message: 'API is running',
        });
    });
}
app.use('/api/*', notFoundHandler);
if (isProduction()) {
    app.use(express.static('public'));
    app.get('*', (req, res) => {
        res.sendFile('index.html', { root: 'public' });
    });
}
app.use(errorHandler);
async function startServer() {
    try {
        logger.info('Starting server initialization...', {
            environment: config.app.env,
            version: config.app.version,
            nodeVersion: process.version,
        });
        await initializeDatabase();
        await initializeRedis();
        await initializePerformanceOptimization();
        const server = app.listen(config.app.port, config.app.host, async () => {
            logger.info('🚀 Server started successfully', {
                port: config.app.port,
                host: config.app.host,
                environment: config.app.env,
                processId: process.pid,
                nodeVersion: process.version,
                urls: {
                    local: `http://localhost:${config.app.port}`,
                    network: `http://${config.app.host}:${config.app.port}`,
                    health: `http://localhost:${config.app.port}/health`,
                    api: `http://localhost:${config.app.port}/api`,
                },
            });
            try {
                await websocketServer.start(server);
                logger.info('🔗 WebSocket server started successfully', {
                    port: config.websocket.port,
                    heartbeatInterval: config.websocket.heartbeatInterval,
                });
            }
            catch (error) {
                logger.error('Failed to start WebSocket server', { error });
            }
            try {
                backupScheduler.start();
                logger.info('📂 Backup scheduler started successfully');
            }
            catch (error) {
                logger.error('Failed to start backup scheduler', { error });
            }
            if (isDevelopment()) {
                logger.info('📡 Available API endpoints:', {
                    auth: `http://localhost:${config.app.port}/api/auth`,
                    users: `http://localhost:${config.app.port}/api/users`,
                    dashboards: `http://localhost:${config.app.port}/api/dashboards`,
                    health: `http://localhost:${config.app.port}/health`,
                });
            }
        });
        server.timeout = config.performance.apiResponseTimeout;
        server.keepAliveTimeout = 65000;
        server.headersTimeout = 66000;
        const shutdown = (signal) => gracefulShutdown(server, signal);
        process.on('SIGTERM', () => shutdown('SIGTERM'));
        process.on('SIGINT', () => shutdown('SIGINT'));
        process.on('uncaughtException', (error) => {
            logger.error('Uncaught Exception', {
                error: error.message,
                stack: error.stack,
            });
            process.exit(1);
        });
        process.on('unhandledRejection', (reason, promise) => {
            logger.error('Unhandled Rejection', {
                reason,
                promise,
            });
        });
        return server;
    }
    catch (error) {
        logger.error('Failed to start server', {
            error: error instanceof Error ? error.message : 'Unknown error',
            stack: error instanceof Error ? error.stack : undefined,
        });
        process.exit(1);
    }
}
if (import.meta.url === `file://${process.argv[1]}`) {
    startServer();
}
export { app, startServer };
export default app;
//# sourceMappingURL=server.js.map