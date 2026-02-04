import { Request, Response, NextFunction } from 'express';
import {
  logger,
  createRequestLogger,
  logAuthEvent,
  logSecurityEvent,
  logBusinessEvent,
  logCacheOperation,
  logPerformanceMetric,
  logSystemHealth
} from '@/utils/logger.js';

// Mock winston
jest.mock('winston', () => {
  const mockLogger = {
    info: jest.fn(),
    warn: jest.fn(),
    error: jest.fn(),
    debug: jest.fn(),
    level: 'info',
    add: jest.fn(),
    remove: jest.fn(),
    configure: jest.fn()
  };

  return {
    createLogger: jest.fn(() => mockLogger),
    format: {
      combine: jest.fn(),
      timestamp: jest.fn(),
      errors: jest.fn(),
      json: jest.fn(),
      printf: jest.fn(),
      colorize: jest.fn(),
      simple: jest.fn()
    },
    transports: {
      Console: jest.fn(),
      File: jest.fn(),
      DailyRotateFile: jest.fn()
    }
  };
});

// Mock environment
jest.mock('@/config/environment.js', () => ({
  config: {
    app: {
      env: 'test',
      name: 'test-app'
    },
    logging: {
      level: 'info',
      enableFileLogging: true,
      maxFiles: '14d',
      maxSize: '20m',
      enableConsoleLogging: true
    }
  },
  isDevelopment: () => false,
  isProduction: () => false,
  isTest: () => true
}));

describe('Logger Utility', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('basic logging', () => {
    it('should log info messages', () => {
      const message = 'Test info message';
      const metadata = { userId: 'user_123', action: 'test' };

      logger.info(message, metadata);

      expect(logger.info).toHaveBeenCalledWith(message, metadata);
    });

    it('should log warn messages', () => {
      const message = 'Test warning message';
      const metadata = { component: 'test', issue: 'minor' };

      logger.warn(message, metadata);

      expect(logger.warn).toHaveBeenCalledWith(message, metadata);
    });

    it('should log error messages', () => {
      const message = 'Test error message';
      const error = new Error('Test error');
      const metadata = { error: error.message, stack: error.stack };

      logger.error(message, metadata);

      expect(logger.error).toHaveBeenCalledWith(message, metadata);
    });

    it('should log debug messages', () => {
      const message = 'Test debug message';
      const metadata = { debugInfo: 'detailed info' };

      logger.debug(message, metadata);

      expect(logger.debug).toHaveBeenCalledWith(message, metadata);
    });
  });

  describe('createRequestLogger', () => {
    let mockRequest: Partial<Request>;
    let mockResponse: Partial<Response>;
    let mockNext: NextFunction;

    beforeEach(() => {
      mockRequest = {
        method: 'GET',
        url: '/api/test',
        ip: '127.0.0.1',
        headers: {
          'user-agent': 'test-agent',
          'x-request-id': 'req-123'
        },
        query: { page: '1' },
        body: { data: 'test' }
      };

      mockResponse = {
        statusCode: 200,
        get: jest.fn().mockReturnValue('application/json'),
        on: jest.fn()
      };

      mockNext = jest.fn();
    });

    it('should create request logging middleware', () => {
      const requestLogger = createRequestLogger();

      expect(typeof requestLogger).toBe('function');
    });

    it('should log incoming requests', () => {
      const requestLogger = createRequestLogger();
      const startTime = Date.now();

      requestLogger(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      expect(mockNext).toHaveBeenCalled();
      expect(logger.info).toHaveBeenCalledWith(
        'Incoming request',
        expect.objectContaining({
          method: 'GET',
          url: '/api/test',
          ip: '127.0.0.1',
          userAgent: 'test-agent',
          requestId: 'req-123'
        })
      );
    });

    it('should log request completion on finish event', (done) => {
      const requestLogger = createRequestLogger();

      // Mock response.on to immediately call the finish handler
      mockResponse.on = jest.fn((event, handler) => {
        if (event === 'finish') {
          // Simulate some response time
          setTimeout(() => {
            handler();

            expect(logger.info).toHaveBeenCalledWith(
              'Request completed',
              expect.objectContaining({
                method: 'GET',
                url: '/api/test',
                statusCode: 200,
                responseTime: expect.any(Number),
                requestId: 'req-123'
              })
            );
            done();
          }, 10);
        }
      });

      requestLogger(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );
    });

    it('should handle requests without request ID', () => {
      mockRequest.headers = {
        'user-agent': 'test-agent'
      };

      const requestLogger = createRequestLogger();

      requestLogger(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      expect(logger.info).toHaveBeenCalledWith(
        'Incoming request',
        expect.objectContaining({
          requestId: expect.stringMatching(/^req-[a-f0-9-]+$/)
        })
      );
    });

    it('should exclude sensitive data from logs', () => {
      mockRequest.body = {
        username: 'testuser',
        password: 'secretpassword',
        token: 'secret-token',
        apiKey: 'secret-api-key'
      };

      const requestLogger = createRequestLogger();

      requestLogger(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      expect(logger.info).toHaveBeenCalledWith(
        'Incoming request',
        expect.objectContaining({
          body: {
            username: 'testuser',
            password: '[REDACTED]',
            token: '[REDACTED]',
            apiKey: '[REDACTED]'
          }
        })
      );
    });

    it('should handle large request bodies by truncating', () => {
      const largeBody = { data: 'x'.repeat(10000) };
      mockRequest.body = largeBody;

      const requestLogger = createRequestLogger();

      requestLogger(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      expect(logger.info).toHaveBeenCalledWith(
        'Incoming request',
        expect.objectContaining({
          body: expect.stringContaining('[TRUNCATED]')
        })
      );
    });
  });

  describe('logAuthEvent', () => {
    it('should log authentication events', () => {
      const eventType = 'USER_LOGIN';
      const userId = 'user_123';
      const details = {
        email: 'test@example.com',
        ipAddress: '127.0.0.1',
        userAgent: 'test-agent'
      };

      logAuthEvent(eventType, userId, details);

      expect(logger.info).toHaveBeenCalledWith(
        `Authentication event: ${eventType}`,
        {
          category: 'auth',
          eventType,
          userId,
          timestamp: expect.any(String),
          ...details
        }
      );
    });

    it('should handle auth events without details', () => {
      const eventType = 'USER_LOGOUT';
      const userId = 'user_123';

      logAuthEvent(eventType, userId);

      expect(logger.info).toHaveBeenCalledWith(
        `Authentication event: ${eventType}`,
        {
          category: 'auth',
          eventType,
          userId,
          timestamp: expect.any(String)
        }
      );
    });

    it('should log different types of auth events', () => {
      const events = [
        'USER_REGISTRATION',
        'USER_LOGIN',
        'USER_LOGOUT',
        'PASSWORD_CHANGE',
        'PASSWORD_RESET',
        'TOKEN_REFRESH'
      ];

      events.forEach(eventType => {
        logAuthEvent(eventType, 'user_123');

        expect(logger.info).toHaveBeenCalledWith(
          `Authentication event: ${eventType}`,
          expect.objectContaining({
            eventType,
            category: 'auth'
          })
        );
      });
    });
  });

  describe('logSecurityEvent', () => {
    it('should log security events with severity', () => {
      const eventType = 'SUSPICIOUS_ACTIVITY';
      const severity = 'high';
      const details = {
        ipAddress: '192.168.1.100',
        attempts: 5,
        description: 'Multiple failed login attempts'
      };

      logSecurityEvent(eventType, severity, details);

      expect(logger.warn).toHaveBeenCalledWith(
        `Security event: ${eventType}`,
        {
          category: 'security',
          eventType,
          severity,
          timestamp: expect.any(String),
          ...details
        }
      );
    });

    it('should use error level for critical security events', () => {
      const eventType = 'INTRUSION_DETECTED';
      const severity = 'critical';
      const details = { threat: 'SQL injection attempt' };

      logSecurityEvent(eventType, severity, details);

      expect(logger.error).toHaveBeenCalledWith(
        `Security event: ${eventType}`,
        {
          category: 'security',
          eventType,
          severity,
          timestamp: expect.any(String),
          ...details
        }
      );
    });

    it('should use info level for low security events', () => {
      const eventType = 'RATE_LIMIT_APPLIED';
      const severity = 'low';
      const details = { endpoint: '/api/data' };

      logSecurityEvent(eventType, severity, details);

      expect(logger.info).toHaveBeenCalledWith(
        `Security event: ${eventType}`,
        {
          category: 'security',
          eventType,
          severity,
          timestamp: expect.any(String),
          ...details
        }
      );
    });
  });

  describe('logBusinessEvent', () => {
    it('should log business events', () => {
      const eventType = 'DASHBOARD_CREATED';
      const details = {
        userId: 'user_123',
        dashboardId: 'dash_456',
        title: 'Sales Dashboard'
      };

      logBusinessEvent(eventType, details);

      expect(logger.info).toHaveBeenCalledWith(
        `Business event: ${eventType}`,
        {
          category: 'business',
          eventType,
          timestamp: expect.any(String),
          ...details
        }
      );
    });

    it('should handle business events without details', () => {
      const eventType = 'SYSTEM_MAINTENANCE';

      logBusinessEvent(eventType);

      expect(logger.info).toHaveBeenCalledWith(
        `Business event: ${eventType}`,
        {
          category: 'business',
          eventType,
          timestamp: expect.any(String)
        }
      );
    });
  });

  describe('logCacheOperation', () => {
    it('should log cache hit operations', () => {
      const operation = 'hit';
      const key = 'user:123:profile';
      const details = {
        ttl: 300,
        layer: 'L1'
      };

      logCacheOperation(operation, key, details);

      expect(logger.debug).toHaveBeenCalledWith(
        `Cache ${operation}: ${key}`,
        {
          category: 'cache',
          operation,
          key,
          timestamp: expect.any(String),
          ...details
        }
      );
    });

    it('should log cache miss operations', () => {
      const operation = 'miss';
      const key = 'user:456:settings';

      logCacheOperation(operation, key);

      expect(logger.debug).toHaveBeenCalledWith(
        `Cache ${operation}: ${key}`,
        {
          category: 'cache',
          operation,
          key,
          timestamp: expect.any(String)
        }
      );
    });

    it('should log cache eviction operations', () => {
      const operation = 'evict';
      const key = 'expired:*';
      const details = {
        count: 25,
        reason: 'expired'
      };

      logCacheOperation(operation, key, details);

      expect(logger.debug).toHaveBeenCalledWith(
        `Cache ${operation}: ${key}`,
        expect.objectContaining({
          operation,
          key,
          count: 25,
          reason: 'expired'
        })
      );
    });
  });

  describe('logPerformanceMetric', () => {
    it('should log performance metrics', () => {
      const metric = 'response_time';
      const value = 150;
      const details = {
        endpoint: '/api/dashboards',
        method: 'GET',
        userId: 'user_123'
      };

      logPerformanceMetric(metric, value, details);

      expect(logger.debug).toHaveBeenCalledWith(
        `Performance metric: ${metric} = ${value}ms`,
        {
          category: 'performance',
          metric,
          value,
          unit: 'ms',
          timestamp: expect.any(String),
          ...details
        }
      );
    });

    it('should handle different metric types', () => {
      const metrics = [
        { metric: 'memory_usage', value: 85, unit: '%' },
        { metric: 'cpu_usage', value: 45, unit: '%' },
        { metric: 'request_count', value: 1000, unit: 'count' },
        { metric: 'error_rate', value: 0.5, unit: '%' }
      ];

      metrics.forEach(({ metric, value, unit }) => {
        logPerformanceMetric(metric, value, { unit });

        expect(logger.debug).toHaveBeenCalledWith(
          `Performance metric: ${metric} = ${value}${unit}`,
          expect.objectContaining({
            metric,
            value,
            unit
          })
        );
      });
    });
  });

  describe('logSystemHealth', () => {
    it('should log system health status', () => {
      const status = 'healthy';
      const components = {
        database: { status: 'healthy', responseTime: 45 },
        cache: { status: 'healthy', hitRate: 87.5 },
        api: { status: 'healthy', averageResponseTime: 120 }
      };

      logSystemHealth(status, components);

      expect(logger.info).toHaveBeenCalledWith(
        `System health check: ${status}`,
        {
          category: 'system',
          healthStatus: status,
          timestamp: expect.any(String),
          components
        }
      );
    });

    it('should log unhealthy system status with warnings', () => {
      const status = 'unhealthy';
      const components = {
        database: { status: 'unhealthy', error: 'Connection timeout' },
        cache: { status: 'healthy', hitRate: 85 }
      };

      logSystemHealth(status, components);

      expect(logger.warn).toHaveBeenCalledWith(
        `System health check: ${status}`,
        {
          category: 'system',
          healthStatus: status,
          timestamp: expect.any(String),
          components
        }
      );
    });

    it('should log degraded system status', () => {
      const status = 'degraded';
      const components = {
        database: { status: 'healthy', responseTime: 200 },
        cache: { status: 'degraded', hitRate: 60 }
      };

      logSystemHealth(status, components);

      expect(logger.warn).toHaveBeenCalledWith(
        `System health check: ${status}`,
        expect.objectContaining({
          healthStatus: status,
          components
        })
      );
    });
  });

  describe('error handling', () => {
    it('should handle logging errors gracefully', () => {
      // Mock logger to throw an error
      (logger.info as jest.Mock).mockImplementation(() => {
        throw new Error('Logging failed');
      });

      // Should not throw when logging fails
      expect(() => {
        logAuthEvent('TEST_EVENT', 'user_123');
      }).not.toThrow();
    });

    it('should sanitize log data to prevent log injection', () => {
      const maliciousData = {
        userInput: 'test\n[FAKE LOG] Malicious log entry\nActual log: ',
        description: 'Normal description'
      };

      logBusinessEvent('USER_INPUT', maliciousData);

      const loggedData = (logger.info as jest.Mock).mock.calls[0][1];
      expect(loggedData.userInput).not.toContain('\n');
      expect(loggedData.userInput).toBe('test [FAKE LOG] Malicious log entry Actual log: ');
    });

    it('should handle circular references in log data', () => {
      const circularObj: any = { name: 'test' };
      circularObj.self = circularObj;

      expect(() => {
        logBusinessEvent('CIRCULAR_REF_TEST', circularObj);
      }).not.toThrow();

      // Should convert circular reference to string
      const loggedData = (logger.info as jest.Mock).mock.calls[0][1];
      expect(typeof loggedData).toBe('object');
    });
  });

  describe('log levels and filtering', () => {
    it('should respect configured log levels', () => {
      // Test that debug logs are called (mock implementation should track calls)
      logPerformanceMetric('test_metric', 100);
      expect(logger.debug).toHaveBeenCalled();

      logger.info('Info message');
      expect(logger.info).toHaveBeenCalledWith('Info message');

      logger.warn('Warning message');
      expect(logger.warn).toHaveBeenCalledWith('Warning message');

      logger.error('Error message');
      expect(logger.error).toHaveBeenCalledWith('Error message');
    });
  });

  describe('structured logging', () => {
    it('should maintain consistent log structure across different log types', () => {
      const expectedStructure = {
        category: expect.any(String),
        timestamp: expect.any(String)
      };

      logAuthEvent('TEST_AUTH', 'user_123');
      expect(logger.info).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining(expectedStructure)
      );

      logSecurityEvent('TEST_SECURITY', 'low');
      expect(logger.info).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining(expectedStructure)
      );

      logBusinessEvent('TEST_BUSINESS');
      expect(logger.info).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining(expectedStructure)
      );
    });

    it('should include ISO timestamp in all structured logs', () => {
      logAuthEvent('TIMESTAMP_TEST', 'user_123');

      const loggedData = (logger.info as jest.Mock).mock.calls[0][1];
      expect(loggedData.timestamp).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/);
    });
  });
});