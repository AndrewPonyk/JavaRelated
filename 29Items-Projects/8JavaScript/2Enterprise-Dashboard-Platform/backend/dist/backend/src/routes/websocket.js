import { Router } from 'express';
import { authenticate, authorize } from '@/middleware/auth/authMiddleware.js';
import { websocketServer } from '@/services/websocket/websocketServer.js';
import { wsRateLimitManager } from '@/middleware/websocket/wsRateLimitMiddleware.js';
import { config } from '@/config/environment.js';
const router = Router();
router.get('/stats', authenticate, authorize('ADMIN', 'SUPER_ADMIN'), (req, res) => {
    try {
        const stats = websocketServer.getStats();
        res.json({
            success: true,
            data: {
                ...stats,
                timestamp: new Date().toISOString(),
                server: {
                    port: config.websocket.port,
                    heartbeatInterval: config.websocket.heartbeatInterval,
                }
            },
            message: 'WebSocket statistics retrieved successfully',
        });
    }
    catch (error) {
        res.status(500).json({
            success: false,
            error: {
                code: 'WEBSOCKET_STATS_ERROR',
                message: 'Failed to retrieve WebSocket statistics',
                statusCode: 500,
            },
        });
    }
});
router.post('/broadcast', authenticate, authorize('ADMIN', 'SUPER_ADMIN'), (req, res) => {
    try {
        const { topic, message, messageType = 'admin_broadcast' } = req.body;
        if (!topic || !message) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'INVALID_REQUEST',
                    message: 'Topic and message are required',
                    statusCode: 400,
                },
            });
        }
        const wsMessage = {
            type: messageType,
            data: message,
            timestamp: Date.now(),
        };
        const sentCount = websocketServer.broadcast(topic, wsMessage);
        res.json({
            success: true,
            data: {
                topic,
                messageType,
                sentCount,
            },
            message: `Message broadcasted to ${sentCount} subscribers`,
        });
    }
    catch (error) {
        res.status(500).json({
            success: false,
            error: {
                code: 'BROADCAST_ERROR',
                message: 'Failed to broadcast message',
                statusCode: 500,
            },
        });
    }
});
router.post('/ban-ip', authenticate, authorize('ADMIN', 'SUPER_ADMIN'), async (req, res) => {
    try {
        const { ip, duration = 300 } = req.body;
        if (!ip) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'INVALID_REQUEST',
                    message: 'IP address is required',
                    statusCode: 400,
                },
            });
        }
        const ipRegex = /^(?:[0-9]{1,3}\.){3}[0-9]{1,3}$|^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$/;
        if (!ipRegex.test(ip)) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'INVALID_IP',
                    message: 'Invalid IP address format',
                    statusCode: 400,
                },
            });
        }
        await wsRateLimitManager.banIP(ip, duration);
        res.json({
            success: true,
            data: {
                ip,
                duration,
                bannedUntil: new Date(Date.now() + duration * 1000).toISOString(),
            },
            message: `IP ${ip} banned for ${duration} seconds`,
        });
    }
    catch (error) {
        res.status(500).json({
            success: false,
            error: {
                code: 'BAN_ERROR',
                message: 'Failed to ban IP address',
                statusCode: 500,
            },
        });
    }
});
router.get('/connections', authenticate, authorize('ADMIN', 'SUPER_ADMIN'), (req, res) => {
    try {
        const stats = websocketServer.getStats();
        res.json({
            success: true,
            data: {
                totalConnections: stats.connections,
                subscriptions: stats.subscriptions,
                connectionsByTopic: stats.connectionsByTopic,
                rateLimits: stats.rateLimitStats,
            },
            message: 'WebSocket connections retrieved successfully',
        });
    }
    catch (error) {
        res.status(500).json({
            success: false,
            error: {
                code: 'CONNECTIONS_ERROR',
                message: 'Failed to retrieve connections',
                statusCode: 500,
            },
        });
    }
});
router.get('/health', (req, res) => {
    try {
        const stats = websocketServer.getStats();
        const isHealthy = stats.connections >= 0;
        res.status(isHealthy ? 200 : 503).json({
            success: isHealthy,
            data: {
                status: isHealthy ? 'healthy' : 'unhealthy',
                connections: stats.connections,
                uptime: process.uptime(),
                timestamp: new Date().toISOString(),
            },
            message: `WebSocket server is ${isHealthy ? 'healthy' : 'unhealthy'}`,
        });
    }
    catch (error) {
        res.status(503).json({
            success: false,
            error: {
                code: 'HEALTH_CHECK_ERROR',
                message: 'WebSocket health check failed',
                statusCode: 503,
            },
        });
    }
});
export default router;
//# sourceMappingURL=websocket.js.map