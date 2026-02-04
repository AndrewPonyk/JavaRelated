import { WebSocket } from 'ws';
import { Server } from 'http';
import { WSRateLimitConfig } from '@/middleware/websocket/wsRateLimitMiddleware.js';
export interface WSMessage {
    type: string;
    data: any;
    timestamp: number;
    id?: string;
}
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
export interface WSServerConfig {
    port?: number;
    heartbeatInterval?: number;
    rateLimitConfig?: Partial<WSRateLimitConfig>;
    enableAuth?: boolean;
    corsOrigins?: string[];
}
export declare class EnterpriseWebSocketServer {
    private wss;
    private server;
    private connections;
    private subscriptions;
    private heartbeatInterval;
    private config;
    constructor(config?: WSServerConfig);
    start(httpServer?: Server): Promise<void>;
    stop(): Promise<void>;
    private handleConnection;
    private setupConnectionHandlers;
    private handleMessage;
    private handleAuthentication;
    private handleSubscription;
    private handleUnsubscription;
    private handleDashboardUpdate;
    private handleWidgetUpdate;
    private handleAnalyticsTracking;
    sendMessage(connectionId: string, message: WSMessage): boolean;
    sendError(connectionId: string, code: string, message: string): void;
    broadcast(topic: string, message: WSMessage, excludeConnections?: string[]): number;
    closeConnection(connectionId: string, code?: number, reason?: string): Promise<void>;
    private handleDisconnection;
    private startHeartbeat;
    getStats(): {
        connections: number;
        subscriptions: number;
        connectionsByTopic: {
            [k: string]: number;
        };
        rateLimitStats: {
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
    };
    private getClientIP;
    private isProtectedTopic;
}
export declare const websocketServer: EnterpriseWebSocketServer;
//# sourceMappingURL=websocketServer.d.ts.map