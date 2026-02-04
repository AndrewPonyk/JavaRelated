import { WebSocket } from 'ws';
import { IncomingMessage } from 'http';
export interface WSRateLimitConfig {
    maxConcurrentConnections: number;
    maxConnectionsPerIP: number;
    maxConnectionsPerUser: number;
    maxMessagesPerMinute: number;
    maxMessagesPerHour: number;
    maxConnectionAttemptsPerMinute: number;
    connectionAttemptWindowMs: number;
    minReconnectDelay: number;
    maxReconnectDelay: number;
    tempBanDuration: number;
    autobanThreshold: number;
}
export declare const defaultWSRateLimitConfig: WSRateLimitConfig;
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
    connectionAttempts: Map<string, {
        count: number;
        firstAttempt: number;
    }>;
    connections: Map<string, ConnectionInfo>;
    userConnections: Map<string, Set<string>>;
    ipConnections: Map<string, Set<string>>;
    messageCounts: Map<string, {
        count: number;
        resetTime: number;
    }>;
    bannedIPs: Map<string, number>;
}
export declare class WSRateLimitManager {
    private config;
    private state;
    constructor(config?: Partial<WSRateLimitConfig>);
    canConnect(req: IncomingMessage): Promise<{
        allowed: boolean;
        reason?: string;
    }>;
    onConnect(ws: WebSocket, req: IncomingMessage): Promise<string>;
    canSendMessage(connectionId: string): Promise<{
        allowed: boolean;
        reason?: string;
    }>;
    onDisconnect(connectionId: string): Promise<void>;
    getStats(): {
        totalConnections: number;
        connectionsPerIP: {
            [k: string]: number;
        };
        connectionsPerUser: {
            [k: string]: number;
        };
        bannedIPs: number;
        limits: WSRateLimitConfig;
    };
    banIP(ip: string, duration?: number): Promise<void>;
    private getClientIP;
    private extractUserId;
    private generateConnectionId;
    private checkConnectionAttempts;
    private recordViolation;
    private persistConnectionInfo;
    private cleanupConnectionInfo;
    private cleanup;
}
export declare const wsRateLimitManager: WSRateLimitManager;
//# sourceMappingURL=wsRateLimitMiddleware.d.ts.map