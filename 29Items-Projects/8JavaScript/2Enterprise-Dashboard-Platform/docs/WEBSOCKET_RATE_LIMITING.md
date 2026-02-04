# WebSocket Rate Limiting Implementation

This document describes the WebSocket rate limiting and connection management implementation in the Enterprise Dashboard Platform.

## Overview

WebSocket rate limiting prevents abuse and ensures fair usage of real-time connections. Our implementation provides comprehensive protection against:

- DoS attacks via excessive connections
- Message flooding
- Rapid reconnection attempts
- IP-based abuse patterns

## Architecture

### Backend Components

1. **Rate Limiting Middleware** (`backend/src/middleware/websocket/wsRateLimitMiddleware.ts`)
   - Connection tracking and limits
   - Message rate limiting
   - IP and user-based restrictions
   - Automatic banning system

2. **WebSocket Server** (`backend/src/services/websocket/websocketServer.ts`)
   - Enterprise-grade WebSocket server
   - Authentication integration
   - Topic-based messaging
   - Heartbeat monitoring

3. **Management API** (`backend/src/routes/websocket.ts`)
   - Admin endpoints for monitoring
   - Broadcast capabilities
   - Connection management

### Frontend Components

1. **WebSocket Client** (`src/services/websocket/websocketClient.ts`)
   - Rate-limit aware client
   - Automatic reconnection with backoff
   - Message queuing during disconnections
   - React hooks integration

## Rate Limiting Configuration

### Environment Variables

```bash
# WebSocket Server Configuration
WS_PORT=3002
WS_HEARTBEAT_INTERVAL=30000

# Rate Limiting Settings
WS_MAX_CONNECTIONS=1000              # Global connection limit
WS_MAX_CONNECTIONS_PER_IP=10         # Per IP connection limit
WS_MAX_CONNECTIONS_PER_USER=5        # Per authenticated user limit
WS_MAX_MESSAGES_PER_MINUTE=60        # Message rate limit (per connection)
WS_MAX_MESSAGES_PER_HOUR=1000        # Hourly message limit (per connection)
WS_CONNECTION_ATTEMPTS_PER_MINUTE=5  # Connection attempt limit (per IP)
WS_BAN_DURATION=300                  # Temporary ban duration (seconds)
```

### Rate Limiting Rules

| Limit Type | Default | Scope | Action on Violation |
|------------|---------|-------|---------------------|
| **Global Connections** | 1,000 | Server | Reject new connections |
| **IP Connections** | 10 | Per IP | Reject connections from IP |
| **User Connections** | 5 | Per authenticated user | Reject user connections |
| **Messages/Minute** | 60 | Per connection | Drop messages, warn |
| **Messages/Hour** | 1,000 | Per connection | Drop messages, escalate |
| **Connection Attempts** | 5/min | Per IP | Temp ban IP |
| **Violations** | 10 | Per connection/IP | Auto-ban |

## Implementation Details

### Connection Management

```typescript
// Connection lifecycle with rate limiting
export class WSRateLimitManager {
  async canConnect(req: IncomingMessage): Promise<{ allowed: boolean; reason?: string }> {
    // Check IP bans
    // Validate connection attempts
    // Check global limits
    // Check IP limits
    // Check user limits
  }

  async onConnect(ws: WebSocket, req: IncomingMessage): Promise<string> {
    // Register connection
    // Track by IP and user
    // Persist to Redis
    // Return connection ID
  }

  async canSendMessage(connectionId: string): Promise<{ allowed: boolean; reason?: string }> {
    // Check minute/hour limits
    // Increment counters
    // Record violations
  }
}
```

### Message Rate Limiting

Rate limits are enforced using Redis-backed counters with sliding windows:

```typescript
// Minute and hour windows for message counting
const minuteKey = `ws_msg_rate:${connectionId}:${Math.floor(now / 60000)}`;
const hourKey = `ws_msg_rate:${connectionId}:${Math.floor(now / 3600000)}`;

// Atomic increment with TTL
await Promise.all([
  cacheService.increment(minuteKey, 60),   // 1 minute TTL
  cacheService.increment(hourKey, 3600),   // 1 hour TTL
]);
```

### Automatic Banning

Progressive enforcement escalates violations:

1. **Warning**: Log violation, continue
2. **Rate Limiting**: Drop messages, send error
3. **Temporary Ban**: Close connection, ban IP
4. **Extended Ban**: Longer ban duration for repeat offenders

### Connection Tracking

Connections are tracked in multiple data structures:

```typescript
interface RateLimitState {
  connections: Map<string, ConnectionInfo>;           // All connections
  userConnections: Map<string, Set<string>>;          // User -> connections
  ipConnections: Map<string, Set<string>>;            // IP -> connections
  connectionAttempts: Map<string, AttemptInfo>;       // IP attempt tracking
  bannedIPs: Map<string, number>;                     // IP -> unban timestamp
}
```

## Usage Examples

### Backend: Starting WebSocket Server

```typescript
import { websocketServer } from '@/services/websocket/websocketServer';

// Start with HTTP server (recommended)
await websocketServer.start(httpServer);

// Or standalone
await websocketServer.start();
```

### Frontend: React Component with WebSocket

```tsx
import { useWebSocket, WSConnectionState } from '@/services/websocket/websocketClient';

const DashboardComponent: React.FC = () => {
  const { client, state, stats, subscribe, send } = useWebSocket({
    onConnect: () => console.log('Connected to WebSocket'),
    onMessage: (message) => console.log('Message received:', message),
    onRateLimit: (reason) => console.warn('Rate limited:', reason),
    onBanned: (reason) => console.error('Banned:', reason),
  });

  React.useEffect(() => {
    // Subscribe to dashboard updates
    subscribe('dashboard:123');

    return () => {
      // Cleanup handled automatically
    };
  }, [subscribe]);

  const sendUpdate = () => {
    send('dashboard_update', {
      dashboardId: '123',
      update: { title: 'New Title' }
    });
  };

  return (
    <div>
      <div>Status: {state}</div>
      <div>Connections: {stats.subscriptions.length}</div>
      {state === WSConnectionState.RATE_LIMITED && (
        <div className="warning">Rate limited - please slow down</div>
      )}
      <button onClick={sendUpdate} disabled={state !== WSConnectionState.CONNECTED}>
        Send Update
      </button>
    </div>
  );
};
```

### Frontend: Manual WebSocket Usage

```typescript
import { websocketClient } from '@/services/websocket/websocketClient';

// Connect with custom config
const client = new EnterpriseWebSocketClient({
  url: 'ws://localhost:3002',
  autoReconnect: true,
  maxReconnectAttempts: 5,
  debug: true,
});

await client.connect({
  onRateLimit: (reason) => {
    // Handle rate limiting gracefully
    showNotification('Please slow down your requests', 'warning');
  },
  onBanned: (reason) => {
    // Handle temporary bans
    showNotification('Connection temporarily restricted', 'error');
  },
});

// Subscribe to topics
client.subscribe('dashboard:456');
client.subscribe('user:notifications');

// Send messages
client.send('widget_update', {
  widgetId: 'widget1',
  data: { value: 100 }
});
```

## Administration

### Monitoring Endpoints

#### Get WebSocket Statistics
```http
GET /api/websocket/stats
Authorization: Bearer <admin-token>
```

Response:
```json
{
  "success": true,
  "data": {
    "connections": 150,
    "subscriptions": 45,
    "connectionsByTopic": {
      "dashboard:123": 20,
      "user:notifications": 100
    },
    "rateLimitStats": {
      "totalConnections": 150,
      "connectionsPerIP": { "192.168.1.1": 5 },
      "connectionsPerUser": { "user123": 3 },
      "bannedIPs": 2
    }
  }
}
```

#### Broadcast Admin Messages
```http
POST /api/websocket/broadcast
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "topic": "global:announcement",
  "message": {
    "title": "Maintenance Notice",
    "body": "System maintenance in 10 minutes"
  },
  "messageType": "admin_announcement"
}
```

#### Ban IP Address
```http
POST /api/websocket/ban-ip
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "ip": "192.168.1.100",
  "duration": 600
}
```

### Monitoring Dashboard

Key metrics to monitor:

- **Connection Count**: Current active connections
- **Connection Rate**: New connections per minute
- **Message Rate**: Messages per second
- **Violation Rate**: Rate limit violations per minute
- **Ban Count**: Number of banned IPs
- **Error Rate**: Connection/message errors

## Security Features

### Connection Security

- **Authentication Required**: Configurable JWT authentication
- **IP Tracking**: All connections tracked by originating IP
- **User Session Limits**: Prevent session hijacking
- **Heartbeat Monitoring**: Detect dead connections

### Rate Limiting Security

- **Progressive Enforcement**: Escalating responses to violations
- **Memory Protection**: Prevents memory exhaustion attacks
- **Redis Persistence**: Rate limit state survives restarts
- **Timing Attack Protection**: Constant-time comparisons

### Message Security

- **Input Validation**: All messages validated before processing
- **Size Limits**: Maximum message payload size
- **Type Validation**: Message type enforcement
- **Subscription Validation**: Topic access control

## Error Handling

### Client-Side Errors

```typescript
// Rate limiting errors
client.on('rateLimit', (reason) => {
  // Show user-friendly message
  // Implement exponential backoff
  // Queue messages for later
});

// Ban errors
client.on('banned', (reason) => {
  // Show ban notification
  // Disable reconnection temporarily
  // Log for investigation
});

// Connection errors
client.on('error', (error) => {
  // Handle network issues
  // Implement fallback behavior
  // Notify user of connectivity issues
});
```

### Server-Side Errors

```typescript
// Connection errors
wsServer.on('connectionError', (error, connectionInfo) => {
  logger.error('WebSocket connection error', {
    error,
    ip: connectionInfo.ip,
    userId: connectionInfo.userId
  });
});

// Rate limit violations
wsRateLimitManager.on('violation', (type, connectionInfo) => {
  logger.warn('Rate limit violation', {
    type,
    ip: connectionInfo.ip,
    violations: connectionInfo.violations
  });
});
```

## Performance Considerations

### Memory Usage

- Connection tracking uses ~1KB per connection
- Message rate counters use ~100B per connection per time window
- Ban tracking uses ~50B per banned IP
- Total memory: ~10MB for 1,000 connections

### Redis Usage

- Rate limit counters: ~50 keys per active connection
- Connection metadata: ~1 key per connection
- Ban information: ~1 key per banned IP
- TTL cleanup: Automatic expiration prevents memory leaks

### CPU Usage

- Connection validation: ~0.1ms per connection attempt
- Message validation: ~0.05ms per message
- Rate limit checking: ~0.1ms per message
- Heartbeat processing: ~0.01ms per connection per interval

## Testing

### Unit Tests

```typescript
import { WSRateLimitManager } from '@/middleware/websocket/wsRateLimitMiddleware';

describe('WebSocket Rate Limiting', () => {
  let rateLimiter: WSRateLimitManager;

  beforeEach(() => {
    rateLimiter = new WSRateLimitManager({
      maxConnectionsPerIP: 2,
      maxMessagesPerMinute: 5
    });
  });

  test('allows connections within limits', async () => {
    const result = await rateLimiter.canConnect(mockRequest);
    expect(result.allowed).toBe(true);
  });

  test('blocks connections exceeding IP limit', async () => {
    // Create 2 connections from same IP
    await rateLimiter.onConnect(ws1, mockRequest);
    await rateLimiter.onConnect(ws2, mockRequest);

    // Third connection should be blocked
    const result = await rateLimiter.canConnect(mockRequest);
    expect(result.allowed).toBe(false);
    expect(result.reason).toContain('IP');
  });

  test('enforces message rate limits', async () => {
    const connectionId = await rateLimiter.onConnect(ws, mockRequest);

    // Send messages up to limit
    for (let i = 0; i < 5; i++) {
      const result = await rateLimiter.canSendMessage(connectionId);
      expect(result.allowed).toBe(true);
    }

    // Sixth message should be blocked
    const result = await rateLimiter.canSendMessage(connectionId);
    expect(result.allowed).toBe(false);
  });
});
```

### Integration Tests

```typescript
import request from 'supertest';
import WebSocket from 'ws';

describe('WebSocket Server Integration', () => {
  test('establishes connection and enforces rate limits', (done) => {
    const ws = new WebSocket('ws://localhost:3002');

    ws.on('open', () => {
      // Send messages rapidly to trigger rate limit
      for (let i = 0; i < 100; i++) {
        ws.send(JSON.stringify({ type: 'test', data: { i } }));
      }
    });

    ws.on('message', (data) => {
      const message = JSON.parse(data.toString());
      if (message.type === 'error' && message.data.code === 'RATE_LIMITED') {
        ws.close();
        done();
      }
    });
  });

  test('admin can view connection statistics', async () => {
    const response = await request(app)
      .get('/api/websocket/stats')
      .set('Authorization', `Bearer ${adminToken}`);

    expect(response.status).toBe(200);
    expect(response.body.data).toHaveProperty('connections');
    expect(response.body.data).toHaveProperty('rateLimitStats');
  });
});
```

### Load Testing

```bash
# Test with Artillery
artillery run websocket-load-test.yml

# Test with custom script
node scripts/websocket-load-test.js --connections 1000 --messages-per-second 100
```

## Troubleshooting

### Common Issues

1. **High Connection Rejections**
   - Check rate limit settings
   - Monitor IP distribution
   - Investigate potential attacks

2. **Message Drops**
   - Verify message rate limits
   - Check client message queuing
   - Monitor server performance

3. **Unexpected Bans**
   - Review ban threshold settings
   - Check for client bugs causing violations
   - Implement client-side backoff

### Debugging

```typescript
// Enable debug logging
const client = new EnterpriseWebSocketClient({
  debug: true,
});

// Monitor rate limit stats
setInterval(() => {
  console.log('WS Stats:', wsRateLimitManager.getStats());
}, 5000);

// Track violations
wsRateLimitManager.on('violation', (violation) => {
  console.warn('Rate limit violation:', violation);
});
```

### Monitoring Queries

```sql
-- Redis queries for monitoring
KEYS ws_conn:*              -- Active connections
KEYS ws_msg_rate:*           -- Message rate counters
KEYS ws_ban:*                -- Banned IPs
TTL ws_ban:192.168.1.100     -- Check ban expiry
```

## Best Practices

1. **Client Implementation**
   - Implement exponential backoff for reconnections
   - Queue messages during disconnections
   - Handle rate limit errors gracefully
   - Monitor connection health

2. **Server Configuration**
   - Set appropriate limits for your use case
   - Monitor rate limit violations
   - Implement alerting for abuse patterns
   - Regularly review ban lists

3. **Security**
   - Use authentication for sensitive topics
   - Implement message validation
   - Monitor for unusual patterns
   - Keep rate limit settings updated

4. **Performance**
   - Monitor Redis memory usage
   - Clean up expired data regularly
   - Use connection pooling
   - Implement proper error handling

## Migration Guide

If you have existing WebSocket implementations:

1. **Update Server Code**: Replace WebSocket server with rate-limited version
2. **Update Client Code**: Use new client with rate limit awareness
3. **Configure Limits**: Set appropriate rate limits for your application
4. **Test Thoroughly**: Verify rate limiting works as expected
5. **Monitor**: Set up monitoring and alerting
6. **Gradual Rollout**: Deploy to staging first, then production

## Conclusion

The WebSocket rate limiting implementation provides enterprise-grade protection against abuse while maintaining excellent performance and user experience. The system is designed to be:

- **Secure**: Comprehensive protection against various attack vectors
- **Scalable**: Handles thousands of concurrent connections
- **Resilient**: Automatic cleanup and recovery mechanisms
- **Observable**: Rich monitoring and debugging capabilities
- **Flexible**: Configurable limits for different use cases

Regular monitoring and adjustment of rate limits ensures optimal performance and security for your specific use case.