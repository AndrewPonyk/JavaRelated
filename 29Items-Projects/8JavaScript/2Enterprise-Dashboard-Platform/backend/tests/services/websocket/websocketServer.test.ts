import { createServer, Server } from 'http';
import { AddressInfo } from 'net';
import WebSocket from 'ws';
import { EnterpriseWebSocketServer } from '@/services/websocket/websocketServer.js';
import { mockRedis } from '../../setup.js';

// Mock Redis
jest.mock('ioredis', () => jest.fn(() => mockRedis));

// Mock authentication
jest.mock('@/middleware/auth/authMiddleware.js', () => ({
  authService: {
    verifyToken: jest.fn()
  }
}));

jest.mock('@/utils/logger.js', () => ({
  logger: {
    info: jest.fn(),
    warn: jest.fn(),
    error: jest.fn()
  }
}));

describe('EnterpriseWebSocketServer', () => {
  let wsServer: EnterpriseWebSocketServer;
  let httpServer: Server;
  let serverPort: number;

  beforeAll(async () => {
    // Create HTTP server for testing
    httpServer = createServer();
    await new Promise<void>((resolve) => {
      httpServer.listen(0, () => {
        serverPort = (httpServer.address() as AddressInfo).port;
        resolve();
      });
    });
  });

  beforeEach(async () => {
    wsServer = EnterpriseWebSocketServer.getInstance();
    clearAllMocks();

    // Reset rate limiting
    mockRedis.get.mockResolvedValue(null);
    mockRedis.setex.mockResolvedValue('OK');
    mockRedis.incr.mockResolvedValue(1);
    mockRedis.expire.mockResolvedValue(1);
  });

  afterEach(async () => {
    if (wsServer) {
      await wsServer.stop();
    }
  });

  afterAll(async () => {
    if (httpServer) {
      await new Promise<void>((resolve) => {
        httpServer.close(() => resolve());
      });
    }
  });

  describe('singleton pattern', () => {
    it('should return the same instance', () => {
      const instance1 = EnterpriseWebSocketServer.getInstance();
      const instance2 = EnterpriseWebSocketServer.getInstance();

      expect(instance1).toBe(instance2);
    });
  });

  describe('server lifecycle', () => {
    it('should start server successfully', async () => {
      const result = await wsServer.start(httpServer);

      expect(result).toEqual({
        success: true,
        port: expect.any(Number),
        message: expect.stringContaining('started')
      });
    });

    it('should handle start errors gracefully', async () => {
      // Try to start on an invalid port
      const invalidServer = createServer();

      // Mock a server error
      jest.spyOn(invalidServer, 'listen').mockImplementation(() => {
        invalidServer.emit('error', new Error('Port already in use'));
        return invalidServer;
      });

      const result = await wsServer.start(invalidServer);

      expect(result.success).toBe(false);
      expect(result.error).toContain('Port already in use');
    });

    it('should stop server gracefully', async () => {
      await wsServer.start(httpServer);
      const result = await wsServer.stop();

      expect(result).toEqual({
        success: true,
        message: expect.stringContaining('stopped')
      });
    });

    it('should handle multiple start attempts gracefully', async () => {
      await wsServer.start(httpServer);
      const secondStart = await wsServer.start(httpServer);

      expect(secondStart.success).toBe(false);
      expect(secondStart.error).toContain('already running');
    });
  });

  describe('connection management', () => {
    beforeEach(async () => {
      await wsServer.start(httpServer);
    });

    it('should accept new WebSocket connections', (done) => {
      const client = new WebSocket(`ws://localhost:${serverPort}`);

      client.on('open', () => {
        const stats = wsServer.getConnectionStats();
        expect(stats.totalConnections).toBe(1);
        expect(stats.activeConnections).toBe(1);
        client.close();
        done();
      });

      client.on('error', done);
    });

    it('should handle connection close', (done) => {
      const client = new WebSocket(`ws://localhost:${serverPort}`);

      client.on('open', () => {
        client.close();
      });

      client.on('close', () => {
        // Give some time for cleanup
        setTimeout(() => {
          const stats = wsServer.getConnectionStats();
          expect(stats.activeConnections).toBe(0);
          done();
        }, 100);
      });

      client.on('error', done);
    });

    it('should enforce connection limits', (done) => {
      const maxConnections = 5;
      const clients: WebSocket[] = [];
      let connectedCount = 0;
      let rejectedCount = 0;

      // Mock connection limit
      (wsServer as any).maxConnections = maxConnections;

      // Try to create more connections than the limit
      for (let i = 0; i < maxConnections + 3; i++) {
        const client = new WebSocket(`ws://localhost:${serverPort}`);

        client.on('open', () => {
          connectedCount++;
          clients.push(client);
        });

        client.on('close', (code) => {
          if (code === 1013) { // Server overloaded
            rejectedCount++;
          }

          if (connectedCount + rejectedCount === maxConnections + 3) {
            expect(connectedCount).toBe(maxConnections);
            expect(rejectedCount).toBe(3);

            // Cleanup
            clients.forEach(c => c.close());
            done();
          }
        });

        client.on('error', () => {
          rejectedCount++;
          if (connectedCount + rejectedCount === maxConnections + 3) {
            expect(connectedCount).toBe(maxConnections);
            expect(rejectedCount).toBe(3);

            // Cleanup
            clients.forEach(c => c.close());
            done();
          }
        });
      }
    });
  });

  describe('authentication', () => {
    beforeEach(async () => {
      await wsServer.start(httpServer);
    });

    it('should authenticate connections with valid token', (done) => {
      const { authService } = require('@/middleware/auth/authMiddleware.js');
      authService.verifyToken.mockResolvedValue({
        id: 'user_123',
        email: 'test@example.com'
      });

      const client = new WebSocket(`ws://localhost:${serverPort}`, {
        headers: {
          'Authorization': 'Bearer valid-token'
        }
      });

      client.on('open', () => {
        expect(authService.verifyToken).toHaveBeenCalledWith('valid-token');
        client.close();
        done();
      });

      client.on('error', done);
    });

    it('should reject connections with invalid token', (done) => {
      const { authService } = require('@/middleware/auth/authMiddleware.js');
      authService.verifyToken.mockRejectedValue(new Error('Invalid token'));

      const client = new WebSocket(`ws://localhost:${serverPort}`, {
        headers: {
          'Authorization': 'Bearer invalid-token'
        }
      });

      client.on('close', (code) => {
        expect(code).toBe(1008); // Policy violation
        expect(authService.verifyToken).toHaveBeenCalledWith('invalid-token');
        done();
      });

      client.on('error', () => {
        // Expected for authentication failure
        done();
      });
    });

    it('should handle missing authentication gracefully', (done) => {
      const client = new WebSocket(`ws://localhost:${serverPort}`);

      client.on('close', (code) => {
        expect(code).toBe(1008); // Policy violation - no auth
        done();
      });

      client.on('error', () => {
        // Expected for missing auth
        done();
      });
    });
  });

  describe('rate limiting', () => {
    beforeEach(async () => {
      await wsServer.start(httpServer);

      // Mock successful authentication
      const { authService } = require('@/middleware/auth/authMiddleware.js');
      authService.verifyToken.mockResolvedValue({
        id: 'user_123',
        email: 'test@example.com'
      });
    });

    it('should enforce connection rate limiting', (done) => {
      const clientIP = '127.0.0.1';
      let connectionAttempts = 0;
      const maxAttempts = 10;

      // Mock rate limit tracking
      mockRedis.get.mockImplementation((key: string) => {
        if (key.includes('connection_rate')) {
          connectionAttempts++;
          return Promise.resolve(connectionAttempts > 5 ? '6' : connectionAttempts.toString());
        }
        return Promise.resolve(null);
      });

      const clients: WebSocket[] = [];
      let rejectedCount = 0;

      for (let i = 0; i < maxAttempts; i++) {
        const client = new WebSocket(`ws://localhost:${serverPort}`, {
          headers: {
            'Authorization': 'Bearer valid-token',
            'X-Forwarded-For': clientIP
          }
        });

        client.on('open', () => {
          clients.push(client);
        });

        client.on('close', (code) => {
          if (code === 1013) { // Rate limited
            rejectedCount++;
          }

          if (clients.length + rejectedCount === maxAttempts) {
            expect(rejectedCount).toBeGreaterThan(0);
            clients.forEach(c => c.close());
            done();
          }
        });

        client.on('error', () => {
          rejectedCount++;
          if (clients.length + rejectedCount === maxAttempts) {
            expect(rejectedCount).toBeGreaterThan(0);
            clients.forEach(c => c.close());
            done();
          }
        });
      }
    });

    it('should enforce message rate limiting', (done) => {
      mockRedis.get.mockResolvedValue('0'); // Start with 0 messages
      mockRedis.incr.mockImplementation((key: string) => {
        const count = key.includes('message_rate') ? 6 : 1; // Simulate exceeding limit
        return Promise.resolve(count);
      });

      const client = new WebSocket(`ws://localhost:${serverPort}`, {
        headers: {
          'Authorization': 'Bearer valid-token'
        }
      });

      client.on('open', () => {
        // Send multiple messages rapidly
        for (let i = 0; i < 10; i++) {
          client.send(JSON.stringify({
            type: 'test',
            data: { message: `Message ${i}` }
          }));
        }
      });

      client.on('close', (code) => {
        if (code === 1013) { // Rate limited
          expect(mockRedis.incr).toHaveBeenCalledWith(
            expect.stringContaining('message_rate')
          );
          done();
        }
      });

      client.on('error', done);
    });
  });

  describe('message handling', () => {
    beforeEach(async () => {
      await wsServer.start(httpServer);

      // Mock successful authentication
      const { authService } = require('@/middleware/auth/authMiddleware.js');
      authService.verifyToken.mockResolvedValue({
        id: 'user_123',
        email: 'test@example.com'
      });

      // Mock rate limiting as passing
      mockRedis.get.mockResolvedValue('0');
      mockRedis.incr.mockResolvedValue(1);
    });

    it('should handle subscription messages', (done) => {
      const client = new WebSocket(`ws://localhost:${serverPort}`, {
        headers: {
          'Authorization': 'Bearer valid-token'
        }
      });

      client.on('open', () => {
        client.send(JSON.stringify({
          type: 'subscribe',
          topic: 'dashboard:updates',
          data: { dashboardId: 'dash_123' }
        }));
      });

      client.on('message', (data) => {
        const message = JSON.parse(data.toString());
        if (message.type === 'subscription_confirmed') {
          expect(message.topic).toBe('dashboard:updates');
          client.close();
          done();
        }
      });

      client.on('error', done);
    });

    it('should handle unsubscription messages', (done) => {
      const client = new WebSocket(`ws://localhost:${serverPort}`, {
        headers: {
          'Authorization': 'Bearer valid-token'
        }
      });

      let subscribed = false;

      client.on('open', () => {
        // First subscribe
        client.send(JSON.stringify({
          type: 'subscribe',
          topic: 'dashboard:updates',
          data: { dashboardId: 'dash_123' }
        }));
      });

      client.on('message', (data) => {
        const message = JSON.parse(data.toString());

        if (message.type === 'subscription_confirmed' && !subscribed) {
          subscribed = true;
          // Then unsubscribe
          client.send(JSON.stringify({
            type: 'unsubscribe',
            topic: 'dashboard:updates'
          }));
        } else if (message.type === 'unsubscription_confirmed') {
          expect(message.topic).toBe('dashboard:updates');
          client.close();
          done();
        }
      });

      client.on('error', done);
    });

    it('should handle invalid message format', (done) => {
      const client = new WebSocket(`ws://localhost:${serverPort}`, {
        headers: {
          'Authorization': 'Bearer valid-token'
        }
      });

      client.on('open', () => {
        client.send('invalid-json');
      });

      client.on('message', (data) => {
        const message = JSON.parse(data.toString());
        if (message.type === 'error') {
          expect(message.error).toContain('Invalid message format');
          client.close();
          done();
        }
      });

      client.on('error', done);
    });

    it('should reject unauthorized topic subscriptions', (done) => {
      const client = new WebSocket(`ws://localhost:${serverPort}`, {
        headers: {
          'Authorization': 'Bearer valid-token'
        }
      });

      client.on('open', () => {
        client.send(JSON.stringify({
          type: 'subscribe',
          topic: 'admin:system',
          data: {}
        }));
      });

      client.on('message', (data) => {
        const message = JSON.parse(data.toString());
        if (message.type === 'error') {
          expect(message.error).toContain('Unauthorized');
          client.close();
          done();
        }
      });

      client.on('error', done);
    });
  });

  describe('broadcasting', () => {
    beforeEach(async () => {
      await wsServer.start(httpServer);

      // Mock successful authentication
      const { authService } = require('@/middleware/auth/authMiddleware.js');
      authService.verifyToken.mockResolvedValue({
        id: 'user_123',
        email: 'test@example.com'
      });

      // Mock rate limiting as passing
      mockRedis.get.mockResolvedValue('0');
      mockRedis.incr.mockResolvedValue(1);
    });

    it('should broadcast messages to subscribed clients', (done) => {
      const clients: WebSocket[] = [];
      const clientsReady: Promise<void>[] = [];

      // Create multiple clients
      for (let i = 0; i < 3; i++) {
        const client = new WebSocket(`ws://localhost:${serverPort}`, {
          headers: {
            'Authorization': 'Bearer valid-token'
          }
        });

        clients.push(client);

        const clientReady = new Promise<void>((resolve) => {
          client.on('open', () => {
            client.send(JSON.stringify({
              type: 'subscribe',
              topic: 'dashboard:updates',
              data: { dashboardId: 'dash_123' }
            }));
          });

          client.on('message', (data) => {
            const message = JSON.parse(data.toString());
            if (message.type === 'subscription_confirmed') {
              resolve();
            } else if (message.type === 'broadcast') {
              expect(message.topic).toBe('dashboard:updates');
              expect(message.data.update).toBe('Dashboard updated');
            }
          });
        });

        clientsReady.push(clientReady);
      }

      Promise.all(clientsReady).then(() => {
        // Broadcast a message
        const broadcastCount = wsServer.broadcast(
          'dashboard:updates',
          {
            type: 'broadcast',
            topic: 'dashboard:updates',
            data: { update: 'Dashboard updated' },
            timestamp: new Date().toISOString()
          }
        );

        expect(broadcastCount).toBe(3);

        // Cleanup
        setTimeout(() => {
          clients.forEach(client => client.close());
          done();
        }, 100);
      });
    });

    it('should exclude specified connections from broadcast', (done) => {
      const clients: WebSocket[] = [];
      const connectionIds: string[] = [];

      // Create two clients
      for (let i = 0; i < 2; i++) {
        const client = new WebSocket(`ws://localhost:${serverPort}`, {
          headers: {
            'Authorization': 'Bearer valid-token'
          }
        });

        clients.push(client);

        client.on('open', () => {
          client.send(JSON.stringify({
            type: 'subscribe',
            topic: 'test:broadcast',
            data: {}
          }));
        });

        client.on('message', (data) => {
          const message = JSON.parse(data.toString());
          if (message.type === 'subscription_confirmed') {
            connectionIds.push(message.connectionId);

            if (connectionIds.length === 2) {
              // Broadcast excluding first connection
              const broadcastCount = wsServer.broadcast(
                'test:broadcast',
                {
                  type: 'broadcast',
                  topic: 'test:broadcast',
                  data: { message: 'Test broadcast' },
                  timestamp: new Date().toISOString()
                },
                [connectionIds[0]]
              );

              expect(broadcastCount).toBe(1); // Only one client should receive it
            }
          }
        });
      }

      // Cleanup after test
      setTimeout(() => {
        clients.forEach(client => client.close());
        done();
      }, 500);
    });
  });

  describe('heartbeat and health monitoring', () => {
    beforeEach(async () => {
      await wsServer.start(httpServer);

      // Mock successful authentication
      const { authService } = require('@/middleware/auth/authMiddleware.js');
      authService.verifyToken.mockResolvedValue({
        id: 'user_123',
        email: 'test@example.com'
      });
    });

    it('should send periodic heartbeat messages', (done) => {
      // Mock shorter heartbeat interval for testing
      (wsServer as any).heartbeatInterval = 100;

      const client = new WebSocket(`ws://localhost:${serverPort}`, {
        headers: {
          'Authorization': 'Bearer valid-token'
        }
      });

      let heartbeatReceived = false;

      client.on('message', (data) => {
        const message = JSON.parse(data.toString());
        if (message.type === 'heartbeat') {
          heartbeatReceived = true;
          expect(message.timestamp).toBeDefined();
          client.close();
          done();
        }
      });

      client.on('error', done);
    });

    it('should detect and handle stale connections', (done) => {
      // Create a client and then simulate it becoming unresponsive
      const client = new WebSocket(`ws://localhost:${serverPort}`, {
        headers: {
          'Authorization': 'Bearer valid-token'
        }
      });

      client.on('open', () => {
        // Mock the ping/pong mechanism
        const originalPing = client.ping;
        let pingCount = 0;

        client.ping = (data?: any, mask?: boolean, cb?: (err: Error) => void) => {
          pingCount++;
          if (pingCount > 3) {
            // Simulate no pong response after 3 pings
            if (cb) cb(new Error('No pong received'));
            return false;
          }
          return originalPing.call(client, data, mask, cb);
        };
      });

      client.on('close', (code) => {
        // Should be closed due to stale connection detection
        expect(code).toBe(1000); // Normal closure or timeout
        done();
      });

      client.on('error', () => {
        // Expected for stale connection
        done();
      });
    });
  });

  describe('statistics and monitoring', () => {
    beforeEach(async () => {
      await wsServer.start(httpServer);
    });

    it('should provide comprehensive connection statistics', () => {
      const stats = wsServer.getConnectionStats();

      expect(stats).toEqual({
        activeConnections: expect.any(Number),
        totalConnections: expect.any(Number),
        totalDisconnections: expect.any(Number),
        rateLimitedConnections: expect.any(Number),
        authenticationFailures: expect.any(Number),
        messagesSent: expect.any(Number),
        messagesReceived: expect.any(Number),
        averageConnectionDuration: expect.any(Number),
        uptime: expect.any(Number)
      });
    });

    it('should track subscription statistics', () => {
      const subscriptionStats = wsServer.getSubscriptionStats();

      expect(subscriptionStats).toEqual({
        totalSubscriptions: expect.any(Number),
        activeTopics: expect.any(Array),
        subscriptionsByTopic: expect.any(Object)
      });
    });

    it('should provide health status information', () => {
      const healthStatus = wsServer.getHealthStatus();

      expect(healthStatus).toEqual({
        status: expect.stringMatching(/healthy|warning|critical/),
        uptime: expect.any(Number),
        connections: expect.any(Object),
        performance: expect.any(Object),
        errors: expect.any(Array)
      });
    });
  });
});