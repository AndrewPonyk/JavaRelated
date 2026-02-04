import { WebSocketServer, WebSocket } from 'ws';
import { Server } from 'http';
import { IncomingMessage } from 'http';
import { wsRateLimitManager, WSRateLimitConfig } from '@/middleware/websocket/wsRateLimitMiddleware.js';
import { authService } from '@/middleware/auth/authMiddleware.js';
import { config } from '@/config/environment.js';
import { logger } from '@/utils/logger.js';

// Message types
export interface WSMessage {
  type: string;
  data: any;
  timestamp: number;
  id?: string;
}

// Connection context
export interface WSConnection {
  id: string;
  ws: WebSocket;
  userId?: string;
  ip: string;
  userAgent?: string;
  subscriptions: Set<string>;
  authenticated: boolean;
  connectedAt: Date;
  lastPong: Date;
}

// WebSocket server configuration
export interface WSServerConfig {
  port?: number;
  heartbeatInterval?: number;
  rateLimitConfig?: Partial<WSRateLimitConfig>;
  enableAuth?: boolean;
  corsOrigins?: string[];
}

export class EnterpriseWebSocketServer {
  private wss: WebSocketServer | null = null;
  private server: Server | null = null;
  private connections: Map<string, WSConnection> = new Map();
  private subscriptions: Map<string, Set<string>> = new Map(); // topic -> Set of connectionIds
  private heartbeatInterval: NodeJS.Timeout | null = null;
  private config: WSServerConfig;

  constructor(config: WSServerConfig = {}) {
    this.config = {
      port: config.port || Number(process.env.WS_PORT) || 3002,
      heartbeatInterval: config.heartbeatInterval || Number(process.env.WS_HEARTBEAT_INTERVAL) || 30000,
      rateLimitConfig: config.rateLimitConfig || {},
      enableAuth: config.enableAuth !== false,
      corsOrigins: config.corsOrigins || ['http://localhost:3000', 'http://localhost:5173'],
    };
  }

  /**
   * Start the WebSocket server
   */
  public async start(httpServer?: Server): Promise<void> {
    try {
      const wsOptions: any = {
        port: httpServer ? undefined : this.config.port,
        server: httpServer,
        perMessageDeflate: {
          zlibDeflateOptions: {
            level: 6,
            chunkSize: 1024,
          },
          zlibInflateOptions: {
            chunkSize: 1024,
          },
          threshold: 1024,
          concurrencyLimit: 10,
          clientMaxNoContextTakeover: false,
          clientMaxWindowBits: 15,
          serverMaxNoContextTakeover: false,
          serverMaxWindowBits: 15,
        },
        maxPayload: 16 * 1024 * 1024, // 16MB
      };

      this.wss = new WebSocketServer(wsOptions);

      // Setup connection handler
      this.wss.on('connection', this.handleConnection.bind(this));

      // Setup error handler
      this.wss.on('error', (error) => {
        logger.error('WebSocket server error', { error });
      });

      // Start heartbeat
      this.startHeartbeat();

      logger.info('WebSocket server started', {
        port: this.config.port,
        heartbeatInterval: this.config.heartbeatInterval,
        enableAuth: this.config.enableAuth,
      });

    } catch (error) {
      logger.error('Failed to start WebSocket server', { error });
      throw error;
    }
  }

  /**
   * Stop the WebSocket server
   */
  public async stop(): Promise<void> {
    logger.info('Stopping WebSocket server...');

    // Stop heartbeat
    if (this.heartbeatInterval) {
      clearInterval(this.heartbeatInterval);
      this.heartbeatInterval = null;
    }

    // Close all connections
    const closePromises = Array.from(this.connections.values()).map(conn =>
      this.closeConnection(conn.id, 1001, 'Server shutting down')
    );

    await Promise.all(closePromises);

    // Close WebSocket server
    if (this.wss) {
      await new Promise<void>((resolve, reject) => {
        this.wss!.close((error) => {
          if (error) reject(error);
          else resolve();
        });
      });
      this.wss = null;
    }

    logger.info('WebSocket server stopped');
  }

  /**
   * Handle new WebSocket connections
   */
  private async handleConnection(ws: WebSocket, req: IncomingMessage): Promise<void> {
    const ip = this.getClientIP(req);
    const userAgent = req.headers['user-agent'];

    try {
      // Check rate limits
      const rateLimitResult = await wsRateLimitManager.canConnect(req);
      if (!rateLimitResult.allowed) {
        logger.warn('WebSocket connection rejected by rate limiter', {
          ip,
          userAgent,
          reason: rateLimitResult.reason,
        });

        ws.close(1008, rateLimitResult.reason || 'Rate limited');
        return;
      }

      // Register connection with rate limiter
      const connectionId = await wsRateLimitManager.onConnect(ws, req);

      // Create connection object
      const connection: WSConnection = {
        id: connectionId,
        ws,
        ip,
        userAgent,
        subscriptions: new Set(),
        authenticated: false,
        connectedAt: new Date(),
        lastPong: new Date(),
      };

      // Store connection
      this.connections.set(connectionId, connection);

      // Setup WebSocket event handlers
      this.setupConnectionHandlers(connection, req);

      // Send welcome message
      this.sendMessage(connectionId, {
        type: 'welcome',
        data: {
          connectionId,
          serverTime: new Date().toISOString(),
          authRequired: this.config.enableAuth,
        },
        timestamp: Date.now(),
      });

      logger.info('New WebSocket connection established', {
        connectionId,
        ip,
        userAgent,
        totalConnections: this.connections.size,
      });

    } catch (error) {
      logger.error('Error handling WebSocket connection', { error, ip, userAgent });
      ws.close(1011, 'Internal server error');
    }
  }

  /**
   * Setup event handlers for a connection
   */
  private setupConnectionHandlers(connection: WSConnection, req: IncomingMessage): void {
    const { id: connectionId, ws } = connection;

    // Handle messages
    ws.on('message', async (data: Buffer) => {
      try {
        // Check message rate limits
        const rateLimitResult = await wsRateLimitManager.canSendMessage(connectionId);
        if (!rateLimitResult.allowed) {
          logger.warn('WebSocket message rejected by rate limiter', {
            connectionId,
            ip: connection.ip,
            reason: rateLimitResult.reason,
          });

          this.sendError(connectionId, 'RATE_LIMITED', rateLimitResult.reason || 'Rate limited');
          return;
        }

        // Parse message
        const rawMessage = data.toString();
        let message: WSMessage;

        try {
          message = JSON.parse(rawMessage);
        } catch (parseError) {
          logger.warn('Invalid WebSocket message format', {
            connectionId,
            ip: connection.ip,
            rawMessage: rawMessage.substring(0, 100),
          });
          this.sendError(connectionId, 'INVALID_FORMAT', 'Message must be valid JSON');
          return;
        }

        // Validate message structure
        if (!message.type || typeof message.type !== 'string') {
          this.sendError(connectionId, 'INVALID_MESSAGE', 'Message must have a type field');
          return;
        }

        // Handle the message
        await this.handleMessage(connectionId, message);

      } catch (error) {
        logger.error('Error processing WebSocket message', {
          error,
          connectionId,
          ip: connection.ip,
        });
        this.sendError(connectionId, 'INTERNAL_ERROR', 'Failed to process message');
      }
    });

    // Handle pong responses
    ws.on('pong', () => {
      connection.lastPong = new Date();
      logger.debug('Received pong from client', { connectionId });
    });

    // Handle connection close
    ws.on('close', async (code: number, reason: Buffer) => {
      logger.info('WebSocket connection closed', {
        connectionId,
        ip: connection.ip,
        code,
        reason: reason.toString(),
        duration: Date.now() - connection.connectedAt.getTime(),
      });

      await this.handleDisconnection(connectionId);
    });

    // Handle connection errors
    ws.on('error', (error) => {
      logger.error('WebSocket connection error', {
        error,
        connectionId,
        ip: connection.ip,
      });
    });
  }

  /**
   * Handle incoming messages
   */
  private async handleMessage(connectionId: string, message: WSMessage): Promise<void> {
    const connection = this.connections.get(connectionId);
    if (!connection) return;

    logger.debug('Processing WebSocket message', {
      connectionId,
      type: message.type,
      dataSize: JSON.stringify(message.data || {}).length,
    });

    switch (message.type) {
      case 'auth':
        await this.handleAuthentication(connectionId, message.data);
        break;

      case 'subscribe':
        await this.handleSubscription(connectionId, message.data);
        break;

      case 'unsubscribe':
        await this.handleUnsubscription(connectionId, message.data);
        break;

      case 'ping':
        this.sendMessage(connectionId, {
          type: 'pong',
          data: { timestamp: Date.now() },
          timestamp: Date.now(),
        });
        break;

      case 'dashboard_update':
        await this.handleDashboardUpdate(connectionId, message.data);
        break;

      case 'widget_update':
        await this.handleWidgetUpdate(connectionId, message.data);
        break;

      case 'analytics_track':
        await this.handleAnalyticsTracking(connectionId, message.data);
        break;

      default:
        logger.warn('Unknown WebSocket message type', {
          connectionId,
          type: message.type,
        });
        this.sendError(connectionId, 'UNKNOWN_MESSAGE_TYPE', `Unknown message type: ${message.type}`);
        break;
    }
  }

  /**
   * Handle user authentication
   */
  private async handleAuthentication(connectionId: string, data: any): Promise<void> {
    const connection = this.connections.get(connectionId);
    if (!connection) return;

    try {
      const { token } = data;
      if (!token) {
        this.sendError(connectionId, 'AUTH_FAILED', 'Token required');
        return;
      }

      // Verify token
      const user = await authService.getUserFromToken(token);
      if (!user) {
        this.sendError(connectionId, 'AUTH_FAILED', 'Invalid token');
        return;
      }

      // Update connection
      connection.authenticated = true;
      connection.userId = user.id;

      logger.info('WebSocket connection authenticated', {
        connectionId,
        userId: user.id,
        email: user.email,
      });

      this.sendMessage(connectionId, {
        type: 'auth_success',
        data: {
          user: {
            id: user.id,
            email: user.email,
            role: user.role,
          },
        },
        timestamp: Date.now(),
      });

    } catch (error) {
      logger.error('WebSocket authentication error', {
        error,
        connectionId,
      });
      this.sendError(connectionId, 'AUTH_FAILED', 'Authentication failed');
    }
  }

  /**
   * Handle topic subscriptions
   */
  private async handleSubscription(connectionId: string, data: any): Promise<void> {
    const connection = this.connections.get(connectionId);
    if (!connection) return;

    const { topic } = data;
    if (!topic || typeof topic !== 'string') {
      this.sendError(connectionId, 'INVALID_TOPIC', 'Topic must be a string');
      return;
    }

    // Check authentication for protected topics
    if (this.isProtectedTopic(topic) && !connection.authenticated) {
      this.sendError(connectionId, 'AUTH_REQUIRED', 'Authentication required for this topic');
      return;
    }

    // Add to subscriptions
    connection.subscriptions.add(topic);

    if (!this.subscriptions.has(topic)) {
      this.subscriptions.set(topic, new Set());
    }
    this.subscriptions.get(topic)!.add(connectionId);

    logger.info('WebSocket subscription added', {
      connectionId,
      userId: connection.userId,
      topic,
    });

    this.sendMessage(connectionId, {
      type: 'subscribed',
      data: { topic },
      timestamp: Date.now(),
    });
  }

  /**
   * Handle topic unsubscriptions
   */
  private async handleUnsubscription(connectionId: string, data: any): Promise<void> {
    const connection = this.connections.get(connectionId);
    if (!connection) return;

    const { topic } = data;
    if (!topic || typeof topic !== 'string') {
      this.sendError(connectionId, 'INVALID_TOPIC', 'Topic must be a string');
      return;
    }

    // Remove from subscriptions
    connection.subscriptions.delete(topic);

    const topicSubs = this.subscriptions.get(topic);
    if (topicSubs) {
      topicSubs.delete(connectionId);
      if (topicSubs.size === 0) {
        this.subscriptions.delete(topic);
      }
    }

    logger.info('WebSocket subscription removed', {
      connectionId,
      userId: connection.userId,
      topic,
    });

    this.sendMessage(connectionId, {
      type: 'unsubscribed',
      data: { topic },
      timestamp: Date.now(),
    });
  }

  /**
   * Handle dashboard updates
   */
  private async handleDashboardUpdate(connectionId: string, data: any): Promise<void> {
    const connection = this.connections.get(connectionId);
    if (!connection || !connection.authenticated) {
      this.sendError(connectionId, 'AUTH_REQUIRED', 'Authentication required');
      return;
    }

    const { dashboardId, update } = data;
    if (!dashboardId) {
      this.sendError(connectionId, 'INVALID_DATA', 'Dashboard ID required');
      return;
    }

    // Broadcast to subscribers
    const topic = `dashboard:${dashboardId}`;
    this.broadcast(topic, {
      type: 'dashboard_updated',
      data: {
        dashboardId,
        update,
        updatedBy: connection.userId,
      },
      timestamp: Date.now(),
    }, [connectionId]); // Exclude sender

    logger.info('Dashboard update broadcasted', {
      dashboardId,
      updatedBy: connection.userId,
      subscriberCount: this.subscriptions.get(topic)?.size || 0,
    });
  }

  /**
   * Handle widget updates
   */
  private async handleWidgetUpdate(connectionId: string, data: any): Promise<void> {
    const connection = this.connections.get(connectionId);
    if (!connection || !connection.authenticated) {
      this.sendError(connectionId, 'AUTH_REQUIRED', 'Authentication required');
      return;
    }

    const { widgetId, dashboardId, update } = data;
    if (!widgetId || !dashboardId) {
      this.sendError(connectionId, 'INVALID_DATA', 'Widget ID and Dashboard ID required');
      return;
    }

    // Broadcast to dashboard subscribers
    const topic = `dashboard:${dashboardId}`;
    this.broadcast(topic, {
      type: 'widget_updated',
      data: {
        widgetId,
        dashboardId,
        update,
        updatedBy: connection.userId,
      },
      timestamp: Date.now(),
    }, [connectionId]);

    logger.info('Widget update broadcasted', {
      widgetId,
      dashboardId,
      updatedBy: connection.userId,
      subscriberCount: this.subscriptions.get(topic)?.size || 0,
    });
  }

  /**
   * Handle analytics tracking
   */
  private async handleAnalyticsTracking(connectionId: string, data: any): Promise<void> {
    const connection = this.connections.get(connectionId);
    if (!connection) return;

    const { event, properties } = data;
    if (!event) {
      this.sendError(connectionId, 'INVALID_DATA', 'Event name required');
      return;
    }

    // Log analytics event
    logger.info('WebSocket analytics event', {
      connectionId,
      userId: connection.userId,
      event,
      properties,
    });

    // You could integrate with your analytics service here
  }

  /**
   * Send message to specific connection
   */
  public sendMessage(connectionId: string, message: WSMessage): boolean {
    const connection = this.connections.get(connectionId);
    if (!connection || connection.ws.readyState !== WebSocket.OPEN) {
      return false;
    }

    try {
      const messageStr = JSON.stringify(message);
      connection.ws.send(messageStr);
      return true;
    } catch (error) {
      logger.error('Failed to send WebSocket message', {
        error,
        connectionId,
        messageType: message.type,
      });
      return false;
    }
  }

  /**
   * Send error message to specific connection
   */
  public sendError(connectionId: string, code: string, message: string): void {
    this.sendMessage(connectionId, {
      type: 'error',
      data: { code, message },
      timestamp: Date.now(),
    });
  }

  /**
   * Broadcast message to topic subscribers
   */
  public broadcast(topic: string, message: WSMessage, excludeConnections: string[] = []): number {
    const subscribers = this.subscriptions.get(topic);
    if (!subscribers) return 0;

    let sentCount = 0;

    for (const connectionId of subscribers) {
      if (excludeConnections.includes(connectionId)) continue;

      if (this.sendMessage(connectionId, message)) {
        sentCount++;
      }
    }

    logger.debug('WebSocket broadcast completed', {
      topic,
      messageType: message.type,
      totalSubscribers: subscribers.size,
      sentCount,
      excludedCount: excludeConnections.length,
    });

    return sentCount;
  }

  /**
   * Close specific connection
   */
  public async closeConnection(connectionId: string, code: number = 1000, reason: string = 'Normal closure'): Promise<void> {
    const connection = this.connections.get(connectionId);
    if (!connection) return;

    try {
      if (connection.ws.readyState === WebSocket.OPEN) {
        connection.ws.close(code, reason);
      }
    } catch (error) {
      logger.error('Error closing WebSocket connection', {
        error,
        connectionId,
      });
    }

    await this.handleDisconnection(connectionId);
  }

  /**
   * Handle connection cleanup
   */
  private async handleDisconnection(connectionId: string): Promise<void> {
    const connection = this.connections.get(connectionId);
    if (!connection) return;

    // Remove from all subscriptions
    for (const topic of connection.subscriptions) {
      const topicSubs = this.subscriptions.get(topic);
      if (topicSubs) {
        topicSubs.delete(connectionId);
        if (topicSubs.size === 0) {
          this.subscriptions.delete(topic);
        }
      }
    }

    // Remove from connections
    this.connections.delete(connectionId);

    // Notify rate limiter
    await wsRateLimitManager.onDisconnect(connectionId);

    logger.info('WebSocket connection cleaned up', {
      connectionId,
      userId: connection.userId,
      ip: connection.ip,
      remainingConnections: this.connections.size,
    });
  }

  /**
   * Start heartbeat mechanism
   */
  private startHeartbeat(): void {
    this.heartbeatInterval = setInterval(() => {
      const now = new Date();
      const timeout = this.config.heartbeatInterval! * 2; // 2x heartbeat interval

      for (const [connectionId, connection] of this.connections) {
        const timeSinceLastPong = now.getTime() - connection.lastPong.getTime();

        if (timeSinceLastPong > timeout) {
          logger.warn('WebSocket connection timed out', {
            connectionId,
            userId: connection.userId,
            timeSinceLastPong,
          });
          this.closeConnection(connectionId, 1001, 'Connection timeout');
        } else if (connection.ws.readyState === WebSocket.OPEN) {
          connection.ws.ping();
        }
      }
    }, this.config.heartbeatInterval);

    logger.info('WebSocket heartbeat started', {
      interval: this.config.heartbeatInterval,
    });
  }

  /**
   * Get server statistics
   */
  public getStats() {
    const connectionsByTopic = new Map<string, number>();
    for (const [topic, subscribers] of this.subscriptions) {
      connectionsByTopic.set(topic, subscribers.size);
    }

    return {
      connections: this.connections.size,
      subscriptions: this.subscriptions.size,
      connectionsByTopic: Object.fromEntries(connectionsByTopic),
      rateLimitStats: wsRateLimitManager.getStats(),
    };
  }

  // Private helper methods
  private getClientIP(req: IncomingMessage): string {
    const xForwardedFor = req.headers['x-forwarded-for'];
    if (xForwardedFor) {
      const forwarded = Array.isArray(xForwardedFor) ? xForwardedFor[0] : xForwardedFor;
      return forwarded?.split(',')[0]?.trim() || 'unknown';
    }
    return req.socket.remoteAddress || 'unknown';
  }

  private isProtectedTopic(topic: string): boolean {
    // Define which topics require authentication
    const protectedPrefixes = ['dashboard:', 'user:', 'admin:'];
    return protectedPrefixes.some(prefix => topic.startsWith(prefix));
  }
}

// Create global instance
export const websocketServer = new EnterpriseWebSocketServer();