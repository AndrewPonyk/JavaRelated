import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import compression from 'compression';
import cookieParser from 'cookie-parser';
import rateLimit from 'express-rate-limit';
import swaggerUi from 'swagger-ui-express';
import 'express-async-errors'; // Handle async errors automatically

// Swagger configuration
import { swaggerSpec } from '@/config/swagger.js';

// Config and database
import { config, isDevelopment, isProduction } from '@/config/environment.js';
import { initializeDatabase } from '@/config/database.js';
import { initializeRedis } from '@/config/redis.js';

// Utilities and middleware
import { logger, createRequestLogger } from '@/utils/logger.js';
import { errorHandler, notFoundHandler, gracefulShutdown } from '@/utils/errors.js';
import { createDataLoaderMiddleware } from '@/utils/dataLoader.js';

// Performance optimization
import {
  initializePerformanceOptimization,
  createOptimizedMiddleware,
  handlePerformanceMetricsRequest,
  handlePerformanceOptimizationRequest
} from '@/utils/performance/index.js';

// Routes
import authRoutes from '@/routes/auth.js';
import userRoutes from '@/routes/users.js';
import dashboardRoutes from '@/routes/dashboard.js';
import websocketRoutes from '@/routes/websocket.js';
import backupRoutes from '@/routes/backup.js';
import dataConnectionRoutes from '@/routes/dataConnections.js';
import calendarRoutes from '@/routes/calendar.js';
import notificationRoutes from '@/routes/notifications.js';
import widgetRoutes from '@/routes/widgets.js';
import searchRoutes from '@/routes/search.js';
import settingsRoutes from '@/routes/settings.js';

// WebSocket Server
import { websocketServer } from '@/services/websocket/websocketServer.js';

// Backup Scheduler
import { backupScheduler } from '@/services/backup/backupScheduler.js';

// Create Express application
const app = express();

// Trust proxy if configured (for accurate IP addresses behind load balancers)
if (config.security.trustProxy) {
  app.set('trust proxy', 1);
}

// Security middleware
if (config.security.enableHelmet) {
  app.use(helmet({
    contentSecurityPolicy: isDevelopment() ? false : undefined,
    crossOriginEmbedderPolicy: false, // Disable for development
  }));
}

// CORS configuration
if (config.security.enableCors) {
  app.use(cors({
    origin: (origin, callback) => {
      // Allow requests with no origin (like mobile apps or curl requests)
      if (!origin) return callback(null, true);

      if (config.cors.origin.includes(origin)) {
        return callback(null, true);
      }

      const error = new Error('Not allowed by CORS');
      callback(error, false);
    },
    methods: config.cors.methods,
    allowedHeaders: config.cors.headers,
    credentials: true, // Allow cookies
    maxAge: 86400, // 24 hours
  }));
}

// Rate limiting (global)
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
    // Skip rate limiting in development
    skip: (req) => isDevelopment(),
  });

  app.use(globalRateLimit);
}

// Body parsing middleware
app.use(express.json({
  limit: '10mb',
  verify: (req: any, res, buf) => {
    // Store raw body for webhook verification if needed
    req.rawBody = buf;
  },
}));

app.use(express.urlencoded({
  extended: true,
  limit: '10mb',
}));

// Cookie parser
app.use(cookieParser());

// Compression middleware
app.use(compression({
  level: 6, // Compression level (0-9)
  threshold: 1024, // Only compress responses > 1KB
  filter: (req, res) => {
    // Don't compress responses if the client doesn't accept gzip
    if (req.headers['x-no-compression']) {
      return false;
    }
    return compression.filter(req, res);
  },
}));

// Request logging middleware
if (!isProduction()) {
  app.use(createRequestLogger());
}

// DataLoader middleware for efficient database queries
app.use(createDataLoaderMiddleware());

// Performance optimization middleware
const performanceMiddleware = createOptimizedMiddleware();
performanceMiddleware.forEach(middleware => app.use(middleware));

// Health check endpoint (before authentication)
app.get('/health', async (req, res) => {
  try {
    // Check database connection
    const dbHealthy = config.healthCheck.database ?
      await import('@/config/database.js').then(db => db.dbManager.healthCheck()) :
      true;

    // Check Redis connection
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
  } catch (error) {
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

// Swagger API Documentation (development and staging)
if (isDevelopment() || config.app.env === 'staging') {
  // Serve Swagger UI
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

  // Serve OpenAPI spec as JSON
  app.get('/api/docs.json', (req, res) => {
    res.setHeader('Content-Type', 'application/json');
    res.send(swaggerSpec);
  });

  logger.info('📖 API Documentation available at /api/docs');
}

// Performance monitoring routes (admin only)
app.get('/api/performance/metrics',
  (req: any, res: any, next: any) => {
    // In a real app, you'd check for admin authentication here
    handlePerformanceMetricsRequest(req, res).catch(next);
  }
);

app.post('/api/performance/optimize',
  (req: any, res: any, next: any) => {
    // In a real app, you'd check for admin authentication here
    handlePerformanceOptimizationRequest(req, res).catch(next);
  }
);

// API routes
app.use('/api/auth', authRoutes);
app.use('/api/users', userRoutes);
app.use('/api/dashboards', dashboardRoutes);
app.use('/api/websocket', websocketRoutes);
app.use('/api/backup', backupRoutes);
app.use('/api/data-connections', dataConnectionRoutes);
app.use('/api/calendar', calendarRoutes);
app.use('/api/notifications', notificationRoutes);
app.use('/api/widgets', widgetRoutes);
app.use('/api/search', searchRoutes);
app.use('/api/settings', settingsRoutes);

// API documentation endpoint (development only)
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
          swagger: '/api/docs', // TODO: Implement Swagger
          postman: '/api/postman', // TODO: Generate Postman collection
        },
      },
      message: 'API is running',
    });
  });
}

// Handle 404 for unknown API routes
app.use('/api/*', notFoundHandler);

// Serve uploaded files (avatars, exports, etc.)
app.use('/uploads', express.static('uploads'));
app.use('/exports', express.static('exports'));

// Serve static files in production (if needed)
if (isProduction()) {
  app.use(express.static('public'));

  // Catch-all handler for SPA routing
  app.get('*', (req, res) => {
    res.sendFile('index.html', { root: 'public' });
  });
}

// Error handling middleware (must be last)
app.use(errorHandler);

// Server initialization
async function startServer() {
  try {
    logger.info('Starting server initialization...', {
      environment: config.app.env,
      version: config.app.version,
      nodeVersion: process.version,
    });

    // Initialize database
    await initializeDatabase();

    // Initialize Redis
    await initializeRedis();

    // Initialize performance optimization
    await initializePerformanceOptimization();

    // Start HTTP server
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

      // Start WebSocket server
      try {
        await websocketServer.start(server);
        logger.info('🔗 WebSocket server started successfully', {
          port: config.websocket.port,
          heartbeatInterval: config.websocket.heartbeatInterval,
        });
      } catch (error) {
        logger.error('Failed to start WebSocket server', { error });
        // Continue without WebSocket if it fails
      }

      // Start backup scheduler
      try {
        backupScheduler.start();
        logger.info('📂 Backup scheduler started successfully');
      } catch (error) {
        logger.error('Failed to start backup scheduler', { error });
        // Continue without backup scheduler if it fails
      }

      // Log available endpoints in development
      if (isDevelopment()) {
        logger.info('📡 Available API endpoints:', {
          auth: `http://localhost:${config.app.port}/api/auth`,
          users: `http://localhost:${config.app.port}/api/users`,
          dashboards: `http://localhost:${config.app.port}/api/dashboards`,
          health: `http://localhost:${config.app.port}/health`,
        });
      }
    });

    // Configure server timeouts
    server.timeout = config.performance.apiResponseTimeout;
    server.keepAliveTimeout = 65000; // Slightly higher than ALB timeout
    server.headersTimeout = 66000; // Higher than keepAliveTimeout

    // Graceful shutdown handling
    const shutdown = (signal: string) => gracefulShutdown(server, signal);

    process.on('SIGTERM', () => shutdown('SIGTERM'));
    process.on('SIGINT', () => shutdown('SIGINT'));

    // Handle uncaught exceptions and unhandled rejections
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

  } catch (error) {
    logger.error('Failed to start server', {
      error: error instanceof Error ? error.message : 'Unknown error',
      stack: error instanceof Error ? error.stack : undefined,
    });
    process.exit(1);
  }
}

// Start server if this file is run directly
const isMainModule = import.meta.url.endsWith('server.ts') ||
                     import.meta.url.endsWith('server.js') ||
                     process.argv[1]?.endsWith('server.ts');
if (isMainModule) {
  startServer();
}

// Export app and startServer for testing
export { app, startServer };
export default app;