import { cacheService } from '@/config/redis.js';
import { logger } from '@/utils/logger.js';
export const defaultWSRateLimitConfig = {
    maxConcurrentConnections: 1000,
    maxConnectionsPerIP: 10,
    maxConnectionsPerUser: 5,
    maxMessagesPerMinute: 60,
    maxMessagesPerHour: 1000,
    maxConnectionAttemptsPerMinute: 5,
    connectionAttemptWindowMs: 60000,
    minReconnectDelay: 1000,
    maxReconnectDelay: 30000,
    tempBanDuration: 300,
    autobanThreshold: 10,
};
export class WSRateLimitManager {
    config;
    state;
    constructor(config = {}) {
        this.config = { ...defaultWSRateLimitConfig, ...config };
        this.state = {
            connectionAttempts: new Map(),
            connections: new Map(),
            userConnections: new Map(),
            ipConnections: new Map(),
            messageCounts: new Map(),
            bannedIPs: new Map(),
        };
        setInterval(() => this.cleanup(), 5 * 60 * 1000);
    }
    async canConnect(req) {
        const ip = this.getClientIP(req);
        const userAgent = req.headers['user-agent'] || 'unknown';
        const userId = this.extractUserId(req);
        try {
            const banTimestamp = this.state.bannedIPs.get(ip);
            if (banTimestamp && Date.now() < banTimestamp) {
                logger.warn('WebSocket connection blocked - IP banned', { ip, userAgent });
                return { allowed: false, reason: 'IP temporarily banned' };
            }
            if (!this.checkConnectionAttempts(ip)) {
                logger.warn('WebSocket connection blocked - too many attempts', { ip, userAgent });
                return { allowed: false, reason: 'Too many connection attempts' };
            }
            if (this.state.connections.size >= this.config.maxConcurrentConnections) {
                logger.warn('WebSocket connection blocked - server capacity', {
                    ip,
                    userAgent,
                    currentConnections: this.state.connections.size
                });
                return { allowed: false, reason: 'Server at capacity' };
            }
            const ipConnections = this.state.ipConnections.get(ip)?.size || 0;
            if (ipConnections >= this.config.maxConnectionsPerIP) {
                logger.warn('WebSocket connection blocked - IP limit', {
                    ip,
                    userAgent,
                    ipConnections
                });
                return { allowed: false, reason: 'Too many connections from this IP' };
            }
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
        }
        catch (error) {
            logger.error('Error checking WebSocket connection limits', { error, ip });
            return { allowed: false, reason: 'Internal error' };
        }
    }
    async onConnect(ws, req) {
        const connectionId = this.generateConnectionId();
        const ip = this.getClientIP(req);
        const userId = this.extractUserId(req);
        const userAgent = req.headers['user-agent'];
        const connectionInfo = {
            id: connectionId,
            ip,
            userId,
            userAgent,
            connectedAt: new Date(),
            lastMessage: new Date(),
            messageCount: 0,
            violations: 0,
        };
        this.state.connections.set(connectionId, connectionInfo);
        if (!this.state.ipConnections.has(ip)) {
            this.state.ipConnections.set(ip, new Set());
        }
        this.state.ipConnections.get(ip).add(connectionId);
        if (userId) {
            if (!this.state.userConnections.has(userId)) {
                this.state.userConnections.set(userId, new Set());
            }
            this.state.userConnections.get(userId).add(connectionId);
        }
        await this.persistConnectionInfo(connectionInfo);
        logger.info('WebSocket connection registered', {
            connectionId,
            ip,
            userId,
            totalConnections: this.state.connections.size,
        });
        return connectionId;
    }
    async canSendMessage(connectionId) {
        const connection = this.state.connections.get(connectionId);
        if (!connection) {
            return { allowed: false, reason: 'Connection not found' };
        }
        const now = Date.now();
        const minuteWindow = now - 60000;
        const hourWindow = now - 3600000;
        try {
            const minuteKey = `ws_msg_rate:${connectionId}:${Math.floor(now / 60000)}`;
            const hourKey = `ws_msg_rate:${connectionId}:${Math.floor(now / 3600000)}`;
            const minuteCount = await cacheService.get(minuteKey) || 0;
            const hourCount = await cacheService.get(hourKey) || 0;
            if (minuteCount >= this.config.maxMessagesPerMinute) {
                this.recordViolation(connectionId, 'message_rate_minute');
                logger.warn('WebSocket message blocked - minute limit', {
                    connectionId,
                    ip: connection.ip,
                    minuteCount
                });
                return { allowed: false, reason: 'Too many messages per minute' };
            }
            if (hourCount >= this.config.maxMessagesPerHour) {
                this.recordViolation(connectionId, 'message_rate_hour');
                logger.warn('WebSocket message blocked - hour limit', {
                    connectionId,
                    ip: connection.ip,
                    hourCount
                });
                return { allowed: false, reason: 'Too many messages per hour' };
            }
            await Promise.all([
                cacheService.increment(minuteKey, 60),
                cacheService.increment(hourKey, 3600),
            ]);
            connection.lastMessage = new Date();
            connection.messageCount++;
            return { allowed: true };
        }
        catch (error) {
            logger.error('Error checking WebSocket message limits', {
                error,
                connectionId,
                ip: connection.ip
            });
            return { allowed: false, reason: 'Internal error' };
        }
    }
    async onDisconnect(connectionId) {
        const connection = this.state.connections.get(connectionId);
        if (!connection)
            return;
        const { ip, userId } = connection;
        this.state.connections.delete(connectionId);
        const ipConnections = this.state.ipConnections.get(ip);
        if (ipConnections) {
            ipConnections.delete(connectionId);
            if (ipConnections.size === 0) {
                this.state.ipConnections.delete(ip);
            }
        }
        if (userId) {
            const userConnections = this.state.userConnections.get(userId);
            if (userConnections) {
                userConnections.delete(connectionId);
                if (userConnections.size === 0) {
                    this.state.userConnections.delete(userId);
                }
            }
        }
        await this.cleanupConnectionInfo(connectionId);
        logger.info('WebSocket connection removed', {
            connectionId,
            ip,
            userId,
            remainingConnections: this.state.connections.size,
        });
    }
    getStats() {
        const ipStats = new Map();
        const userStats = new Map();
        for (const connection of this.state.connections.values()) {
            const ipCount = ipStats.get(connection.ip) || 0;
            ipStats.set(connection.ip, ipCount + 1);
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
    async banIP(ip, duration = this.config.tempBanDuration) {
        const banUntil = Date.now() + (duration * 1000);
        this.state.bannedIPs.set(ip, banUntil);
        const connectionsToClose = Array.from(this.state.connections.values())
            .filter(conn => conn.ip === ip);
        logger.warn('Banning IP from WebSocket', {
            ip,
            duration,
            connectionsToClose: connectionsToClose.length
        });
        await cacheService.set(`ws_ban:${ip}`, banUntil, duration);
        return Promise.resolve();
    }
    getClientIP(req) {
        const xForwardedFor = req.headers['x-forwarded-for'];
        if (xForwardedFor) {
            return (Array.isArray(xForwardedFor) ? xForwardedFor[0] : xForwardedFor).split(',')[0].trim();
        }
        return req.socket.remoteAddress || 'unknown';
    }
    extractUserId(req) {
        const url = new URL(req.url || '', `http://${req.headers.host}`);
        const token = url.searchParams.get('token') || req.headers.authorization?.replace('Bearer ', '');
        if (token) {
            try {
                const payload = JSON.parse(Buffer.from(token.split('.')[1], 'base64').toString());
                return payload.userId;
            }
            catch (error) {
            }
        }
        return undefined;
    }
    generateConnectionId() {
        return `ws_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    }
    checkConnectionAttempts(ip) {
        const now = Date.now();
        const attempts = this.state.connectionAttempts.get(ip);
        if (!attempts) {
            this.state.connectionAttempts.set(ip, { count: 1, firstAttempt: now });
            return true;
        }
        if (now - attempts.firstAttempt > this.config.connectionAttemptWindowMs) {
            this.state.connectionAttempts.set(ip, { count: 1, firstAttempt: now });
            return true;
        }
        if (attempts.count >= this.config.maxConnectionAttemptsPerMinute) {
            if (attempts.count >= this.config.autobanThreshold) {
                this.banIP(ip);
            }
            return false;
        }
        attempts.count++;
        return true;
    }
    recordViolation(connectionId, type) {
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
            if (connection.violations >= this.config.autobanThreshold) {
                this.banIP(connection.ip);
            }
        }
    }
    async persistConnectionInfo(connection) {
        const key = `ws_conn:${connection.id}`;
        await cacheService.set(key, connection, 3600);
    }
    async cleanupConnectionInfo(connectionId) {
        const key = `ws_conn:${connectionId}`;
        await cacheService.delete(key);
    }
    cleanup() {
        const now = Date.now();
        for (const [ip, attempts] of this.state.connectionAttempts) {
            if (now - attempts.firstAttempt > this.config.connectionAttemptWindowMs * 2) {
                this.state.connectionAttempts.delete(ip);
            }
        }
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
export const wsRateLimitManager = new WSRateLimitManager();
//# sourceMappingURL=wsRateLimitMiddleware.js.map