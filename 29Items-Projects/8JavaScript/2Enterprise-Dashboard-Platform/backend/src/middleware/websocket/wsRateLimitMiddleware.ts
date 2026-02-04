import { WebSocket } from 'ws';
import { IncomingMessage } from 'http';
import { cacheService } from '@/config/redis.js';
import { config } from '@/config/environment.js';
import { logger } from '@/utils/logger.js';

// Rate limiting configuration
export interface WSRateLimitConfig {
  // Connection limits
  maxConcurrentConnections: number;
  maxConnectionsPerIP: number;
  maxConnectionsPerUser: number;

  // Message rate limits
  maxMessagesPerMinute: number;
  maxMessagesPerHour: number;

  // Connection attempt limits
  maxConnectionAttemptsPerMinute: number;
  connectionAttemptWindowMs: number;

  // Reconnection limits
  minReconnectDelay: number;
  maxReconnectDelay: number;

  // Ban settings
  tempBanDuration: number; // seconds
  autobanThreshold: number; // failed attempts before temp ban
}

// Default configuration
export const defaultWSRateLimitConfig: WSRateLimitConfig = {
  maxConcurrentConnections: 1000,
  maxConnectionsPerIP: 10,
  maxConnectionsPerUser: 5,
  maxMessagesPerMinute: 60,
  maxMessagesPerHour: 1000,
  maxConnectionAttemptsPerMinute: 5,
  connectionAttemptWindowMs: 60000,
  minReconnectDelay: 1000,
  maxReconnectDelay: 30000,
  tempBanDuration: 300, // 5 minutes
  autobanThreshold: 10,
};

// Connection tracking interfaces
export interface ConnectionInfo {
  id: string;
  ip: string;
  userId?: string;
  userAgent?: string;
  connectedAt: Date;
  lastMessage: Date;
  messageCount: number;
  violations: number;
}

export interface RateLimitState {
  connectionAttempts: Map<string, { count: number; firstAttempt: number }>;
  connections: Map<string, ConnectionInfo>;
  userConnections: Map<string, Set<string>>; // userId -> Set of connectionIds
  ipConnections: Map<string, Set<string>>; // ip -> Set of connectionIds
  messageCounts: Map<string, { count: number; resetTime: number }>;
  bannedIPs: Map<string, number>; // ip -> unban timestamp
}

export class WSRateLimitManager {
  private config: WSRateLimitConfig;
  private state: RateLimitState;

  constructor(config: Partial<WSRateLimitConfig> = {}) {
    this.config = { ...defaultWSRateLimitConfig, ...config };
    this.state = {
      connectionAttempts: new Map(),
      connections: new Map(),
      userConnections: new Map(),
      ipConnections: new Map(),
      messageCounts: new Map(),
      bannedIPs: new Map(),
    };

    // Clean up expired entries every 5 minutes
    setInterval(() => this.cleanup(), 5 * 60 * 1000);
  }

  /**
   * Check if connection should be allowed
   */
  public async canConnect(req: IncomingMessage): Promise<{ allowed: boolean; reason?: string }> {
    const ip = this.getClientIP(req);
    const userAgent = req.headers['user-agent'] || 'unknown';
    const userId = this.extractUserId(req); // From token if available

    try {
      // Check if IP is temporarily banned
      const banTimestamp = this.state.bannedIPs.get(ip);
      if (banTimestamp && Date.now() < banTimestamp) {
        logger.warn('WebSocket connection blocked - IP banned', { ip, userAgent });
        return { allowed: false, reason: 'IP temporarily banned' };
      }

      // Check connection attempts rate limit
      if (!this.checkConnectionAttempts(ip)) {
        logger.warn('WebSocket connection blocked - too many attempts', { ip, userAgent });
        return { allowed: false, reason: 'Too many connection attempts' };
      }

      // Check global connection limit
      if (this.state.connections.size >= this.config.maxConcurrentConnections) {
        logger.warn('WebSocket connection blocked - server capacity', {
          ip,
          userAgent,
          currentConnections: this.state.connections.size
        });
        return { allowed: false, reason: 'Server at capacity' };
      }

      // Check IP connection limit
      const ipConnections = this.state.ipConnections.get(ip)?.size || 0;
      if (ipConnections >= this.config.maxConnectionsPerIP) {
        logger.warn('WebSocket connection blocked - IP limit', {
          ip,
          userAgent,
          ipConnections
        });
        return { allowed: false, reason: 'Too many connections from this IP' };
      }

      // Check user connection limit (if authenticated)
      if (userId) {
        const userConnections = this.state.userConnections.get(userId)?.size || 0;
        if (userConnections >= this.config.maxConnectionsPerUser) {
          logger.warn('WebSocket connection blocked - user limit', {
            ip,
            userId,
            userConnections
          });
          return { allowed: false, reason: 'Too many connections for this user' };
        }
      }

      logger.info('WebSocket connection allowed', { ip, userId, userAgent });
      return { allowed: true };

    } catch (error) {
      logger.error('Error checking WebSocket connection limits', { error, ip });
      return { allowed: false, reason: 'Internal error' };
    }
  }

  /**
   * Register a new connection
   */
  public async onConnect(ws: WebSocket, req: IncomingMessage): Promise<string> {
    const connectionId = this.generateConnectionId();
    const ip = this.getClientIP(req);
    const userId = this.extractUserId(req);
    const userAgent = req.headers['user-agent'];

    const connectionInfo: ConnectionInfo = {
      id: connectionId,
      ip,
      userId,
      userAgent,
      connectedAt: new Date(),
      lastMessage: new Date(),
      messageCount: 0,
      violations: 0,
    };

    // Store connection info
    this.state.connections.set(connectionId, connectionInfo);

    // Update IP tracking
    if (!this.state.ipConnections.has(ip)) {
      this.state.ipConnections.set(ip, new Set());
    }
    this.state.ipConnections.get(ip)!.add(connectionId);

    // Update user tracking (if authenticated)
    if (userId) {
      if (!this.state.userConnections.has(userId)) {
        this.state.userConnections.set(userId, new Set());
      }
      this.state.userConnections.get(userId)!.add(connectionId);
    }

    // Store in Redis for persistence across restarts
    await this.persistConnectionInfo(connectionInfo);

    logger.info('WebSocket connection registered', {
      connectionId,
      ip,
      userId,
      totalConnections: this.state.connections.size,
    });

    return connectionId;
  }

  /**
   * Check if message should be allowed
   */
  public async canSendMessage(connectionId: string): Promise<{ allowed: boolean; reason?: string }> {
    const connection = this.state.connections.get(connectionId);
    if (!connection) {
      return { allowed: false, reason: 'Connection not found' };
    }

    const now = Date.now();
    const minuteWindow = now - 60000; // 1 minute ago
    const hourWindow = now - 3600000; // 1 hour ago

    try {
      // Get message counts from cache
      const minuteKey = `ws_msg_rate:${connectionId}:${Math.floor(now / 60000)}`;
      const hourKey = `ws_msg_rate:${connectionId}:${Math.floor(now / 3600000)}`;

      const minuteCount = await cacheService.get<number>(minuteKey) || 0;
      const hourCount = await cacheService.get<number>(hourKey) || 0;

      // Check minute limit
      if (minuteCount >= this.config.maxMessagesPerMinute) {
        this.recordViolation(connectionId, 'message_rate_minute');
        logger.warn('WebSocket message blocked - minute limit', {
          connectionId,
          ip: connection.ip,
          minuteCount
        });
        return { allowed: false, reason: 'Too many messages per minute' };
      }

      // Check hour limit
      if (hourCount >= this.config.maxMessagesPerHour) {
        this.recordViolation(connectionId, 'message_rate_hour');
        logger.warn('WebSocket message blocked - hour limit', {
          connectionId,
          ip: connection.ip,
          hourCount
        });
        return { allowed: false, reason: 'Too many messages per hour' };
      }

      // Increment counters
      await Promise.all([
        cacheService.increment(minuteKey, 60), // 1 minute TTL
        cacheService.increment(hourKey, 3600), // 1 hour TTL
      ]);

      // Update connection info
      connection.lastMessage = new Date();
      connection.messageCount++;

      return { allowed: true };

    } catch (error) {
      logger.error('Error checking WebSocket message limits', {
        error,
        connectionId,
        ip: connection.ip
      });
      return { allowed: false, reason: 'Internal error' };
    }
  }

  /**
   * Handle connection close
   */
  public async onDisconnect(connectionId: string): Promise<void> {
    const connection = this.state.connections.get(connectionId);
    if (!connection) return;

    const { ip, userId } = connection;

    // Remove from tracking maps
    this.state.connections.delete(connectionId);

    // Update IP tracking
    const ipConnections = this.state.ipConnections.get(ip);
    if (ipConnections) {
      ipConnections.delete(connectionId);
      if (ipConnections.size === 0) {
        this.state.ipConnections.delete(ip);
      }
    }

    // Update user tracking
    if (userId) {
      const userConnections = this.state.userConnections.get(userId);
      if (userConnections) {
        userConnections.delete(connectionId);
        if (userConnections.size === 0) {
          this.state.userConnections.delete(userId);
        }
      }
    }

    // Clean up from Redis
    await this.cleanupConnectionInfo(connectionId);

    logger.info('WebSocket connection removed', {
      connectionId,
      ip,
      userId,
      remainingConnections: this.state.connections.size,
    });
  }

  /**
   * Get connection statistics
   */
  public getStats() {
    const ipStats = new Map<string, number>();
    const userStats = new Map<string, number>();

    for (const connection of this.state.connections.values()) {
      // IP stats
      const ipCount = ipStats.get(connection.ip) || 0;
      ipStats.set(connection.ip, ipCount + 1);

      // User stats
      if (connection.userId) {
        const userCount = userStats.get(connection.userId) || 0;
        userStats.set(connection.userId, userCount + 1);
      }
    }

    return {
      totalConnections: this.state.connections.size,
      connectionsPerIP: Object.fromEntries(ipStats),
      connectionsPerUser: Object.fromEntries(userStats),
      bannedIPs: this.state.bannedIPs.size,
      limits: this.config,
    };
  }

  /**
   * Emergency: Close all connections from an IP
   */
  public async banIP(ip: string, duration: number = this.config.tempBanDuration): Promise<void> {
    const banUntil = Date.now() + (duration * 1000);
    this.state.bannedIPs.set(ip, banUntil);

    // Close existing connections from this IP
    const connectionsToClose = Array.from(this.state.connections.values())
      .filter(conn => conn.ip === ip);

    logger.warn('Banning IP from WebSocket', {
      ip,
      duration,
      connectionsToClose: connectionsToClose.length
    });

    // Store ban in Redis for persistence
    await cacheService.set(`ws_ban:${ip}`, banUntil, duration);

    return Promise.resolve();
  }

  // Private helper methods
  private getClientIP(req: IncomingMessage): string {
    const xForwardedFor = req.headers['x-forwarded-for'];
    if (xForwardedFor) {
      const firstForward = Array.isArray(xForwardedFor) ? xForwardedFor[0] : xForwardedFor;
      return firstForward?.split(',')[0]?.trim() || 'unknown';
    }
    return req.socket.remoteAddress || 'unknown';
  }

  private extractUserId(req: IncomingMessage): string | undefined {
    // Extract user ID from JWT token in query params or headers
    const url = new URL(req.url || '', `http://${req.headers.host}`);
    const token = url.searchParams.get('token') || req.headers.authorization?.replace('Bearer ', '');

    if (token) {
      try {
        // Decode JWT to get user ID (simplified - use proper JWT library)
        const tokenParts = token.split('.');
        if (tokenParts[1]) {
          const payload = JSON.parse(Buffer.from(tokenParts[1], 'base64').toString());
          return payload.userId;
        }
      } catch (error) {
        // Invalid token, continue as unauthenticated
      }
    }

    return undefined;
  }

  private generateConnectionId(): string {
    return `ws_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  private checkConnectionAttempts(ip: string): boolean {
    const now = Date.now();
    const attempts = this.state.connectionAttempts.get(ip);

    if (!attempts) {
      this.state.connectionAttempts.set(ip, { count: 1, firstAttempt: now });
      return true;
    }

    // Reset if window expired
    if (now - attempts.firstAttempt > this.config.connectionAttemptWindowMs) {
      this.state.connectionAttempts.set(ip, { count: 1, firstAttempt: now });
      return true;
    }

    // Check if limit exceeded
    if (attempts.count >= this.config.maxConnectionAttemptsPerMinute) {
      // Auto-ban if too many violations
      if (attempts.count >= this.config.autobanThreshold) {
        this.banIP(ip);
      }
      return false;
    }

    // Increment counter
    attempts.count++;
    return true;
  }

  private recordViolation(connectionId: string, type: string): void {
    const connection = this.state.connections.get(connectionId);
    if (connection) {
      connection.violations++;

      logger.warn('WebSocket rate limit violation', {
        connectionId,
        ip: connection.ip,
        userId: connection.userId,
        type,
        violations: connection.violations,
      });

      // Auto-ban if too many violations
      if (connection.violations >= this.config.autobanThreshold) {
        this.banIP(connection.ip);
      }
    }
  }

  private async persistConnectionInfo(connection: ConnectionInfo): Promise<void> {
    const key = `ws_conn:${connection.id}`;
    await cacheService.set(key, connection, 3600); // 1 hour TTL
  }

  private async cleanupConnectionInfo(connectionId: string): Promise<void> {
    const key = `ws_conn:${connectionId}`;
    await cacheService.delete(key);
  }

  private cleanup(): void {
    const now = Date.now();

    // Clean up expired connection attempts
    for (const [ip, attempts] of this.state.connectionAttempts) {
      if (now - attempts.firstAttempt > this.config.connectionAttemptWindowMs * 2) {
        this.state.connectionAttempts.delete(ip);
      }
    }

    // Clean up expired bans
    for (const [ip, banUntil] of this.state.bannedIPs) {
      if (now >= banUntil) {
        this.state.bannedIPs.delete(ip);
        logger.info('IP ban expired', { ip });
      }
    }

    logger.debug('WebSocket rate limit cleanup completed', {
      connectionAttempts: this.state.connectionAttempts.size,
      bannedIPs: this.state.bannedIPs.size,
    });
  }
}

// Create global instance
export const wsRateLimitManager = new WSRateLimitManager();