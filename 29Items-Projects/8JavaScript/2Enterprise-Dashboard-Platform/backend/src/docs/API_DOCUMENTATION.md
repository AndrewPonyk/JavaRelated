# Enterprise Dashboard Platform API Documentation

This document provides comprehensive information about the Enterprise Dashboard Platform REST API and WebSocket services.

## 📖 Interactive Documentation

The complete API documentation is available through Swagger UI when running the application in development or staging mode:

- **Swagger UI**: `http://localhost:3001/api/docs`
- **OpenAPI Spec**: `http://localhost:3001/api/docs.json`

## 🚀 Quick Start

### Base URL
```
Development: http://localhost:3001/api
Production: https://your-domain.com/api
```

### Authentication

Most endpoints require authentication using JWT Bearer tokens:

```bash
# Login to get token
curl -X POST http://localhost:3001/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com", "password": "password"}'

# Use token in subsequent requests
curl -X GET http://localhost:3001/api/dashboards \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### CSRF Protection

State-changing operations (POST, PUT, DELETE) require CSRF tokens:

```bash
# Get CSRF token
curl -X GET http://localhost:3001/api/auth/csrf-token

# Include in header for state-changing operations
curl -X POST http://localhost:3001/api/dashboards \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "X-CSRF-Token: YOUR_CSRF_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title": "New Dashboard"}'
```

## 📚 API Endpoints

### Authentication & Users
- `POST /api/auth/login` - User login
- `POST /api/auth/register` - User registration
- `POST /api/auth/logout` - User logout
- `GET /api/auth/me` - Get current user
- `GET /api/users` - List users (Admin)
- `GET /api/users/{id}` - Get user by ID
- `PUT /api/users/{id}` - Update user
- `DELETE /api/users/{id}` - Delete user (Admin)

### Dashboards
- `GET /api/dashboards` - List user dashboards
- `POST /api/dashboards` - Create dashboard
- `GET /api/dashboards/{id}` - Get dashboard
- `PUT /api/dashboards/{id}` - Update dashboard
- `DELETE /api/dashboards/{id}` - Delete dashboard
- `POST /api/dashboards/{id}/share` - Share dashboard
- `GET /api/dashboards/{id}/analytics` - Get analytics

### Backup & Administration
- `GET /api/backup/list` - List backups (Admin)
- `POST /api/backup/create` - Create backup (Admin)
- `POST /api/backup/restore` - Restore backup (Super Admin)
- `GET /api/backup/stats` - Backup statistics (Admin)

### System & Health
- `GET /health` - System health check
- `GET /api/websocket/info` - WebSocket information

## 🔌 WebSocket API

### Connection
```javascript
const token = 'YOUR_JWT_TOKEN';
const ws = new WebSocket(`ws://localhost:3002?token=${token}`);
```

### Message Format
All WebSocket messages follow this structure:
```json
{
  "type": "message_type",
  "topic": "optional_topic",
  "data": { /* payload */ },
  "timestamp": "2024-01-15T10:30:00.000Z",
  "messageId": "unique_id"
}
```

### Subscription Example
```javascript
// Subscribe to dashboard updates
ws.send(JSON.stringify({
  type: 'subscribe',
  data: {
    topics: ['dashboard:dash_123456789']
  },
  timestamp: new Date().toISOString()
}));

// Listen for updates
ws.onmessage = (event) => {
  const message = JSON.parse(event.data);
  if (message.type === 'dashboard_update') {
    console.log('Dashboard updated:', message.data);
  }
};
```

## 📊 Response Format

### Success Response
```json
{
  "success": true,
  "data": { /* response data */ },
  "message": "Operation completed successfully",
  "timestamp": "2024-01-15T10:30:00.000Z"
}
```

### Error Response
```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "Human readable error message",
    "statusCode": 400,
    "details": { /* additional context */ }
  },
  "requestId": "req-123456789",
  "timestamp": "2024-01-15T10:30:00.000Z"
}
```

### Paginated Response
```json
{
  "success": true,
  "data": {
    "items": [ /* array of items */ ],
    "totalItems": 100,
    "totalPages": 10,
    "currentPage": 1,
    "limit": 10,
    "hasNext": true,
    "hasPrev": false
  },
  "message": "Data retrieved successfully"
}
```

## 🔐 Security

### Rate Limiting
- **Global**: 100 requests per 15 minutes
- **Authentication**: 10 requests per 15 minutes
- **Dashboard**: 200 requests per 15 minutes
- **Users**: 100 requests per 15 minutes
- **Backup**: 5 requests per 15 minutes (sensitive operations)

### Headers
```
Authorization: Bearer <jwt-token>
X-CSRF-Token: <csrf-token>
Content-Type: application/json
```

## 📋 Common Use Cases

### 1. User Registration & Login
```bash
# Register new user
curl -X POST http://localhost:3001/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "securePassword123",
    "name": "John Doe"
  }'

# Login
curl -X POST http://localhost:3001/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "securePassword123"
  }'
```

### 2. Creating a Dashboard
```bash
# Get CSRF token first
CSRF_TOKEN=$(curl -s http://localhost:3001/api/auth/csrf-token | jq -r '.data.csrfToken')

# Create dashboard
curl -X POST http://localhost:3001/api/dashboards \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "X-CSRF-Token: $CSRF_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Sales Analytics Dashboard",
    "description": "Monthly sales performance metrics",
    "isPublic": false,
    "layout": {
      "rows": 4,
      "cols": 12,
      "gap": 16
    },
    "tags": ["sales", "analytics"]
  }'
```

### 3. Dashboard Analytics
```bash
curl -X GET "http://localhost:3001/api/dashboards/dash_123456789/analytics?startDate=2024-01-01&endDate=2024-01-31&granularity=day" \
  -H "Authorization: Bearer $JWT_TOKEN"
```

### 4. Sharing a Dashboard
```bash
curl -X POST http://localhost:3001/api/dashboards/dash_123456789/share \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "X-CSRF-Token: $CSRF_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "shares": [
      {
        "userId": "usr_987654321",
        "permission": "read"
      }
    ],
    "notify": true,
    "message": "Check out this dashboard!"
  }'
```

### 5. Creating a Backup (Admin)
```bash
curl -X POST http://localhost:3001/api/backup/create \
  -H "Authorization: Bearer $ADMIN_JWT_TOKEN" \
  -H "X-CSRF-Token: $CSRF_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "customSuffix": "pre-upgrade",
    "description": "Backup before system upgrade",
    "compression": true
  }'
```

## 🛠️ Development Tools

### Testing with cURL
```bash
# Set environment variables
export API_BASE="http://localhost:3001/api"
export JWT_TOKEN="your_jwt_token"
export CSRF_TOKEN="your_csrf_token"

# Test authentication
curl -X POST $API_BASE/auth/verify \
  -H "Authorization: Bearer $JWT_TOKEN"

# Test dashboard creation
curl -X POST $API_BASE/dashboards \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "X-CSRF-Token: $CSRF_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title": "Test Dashboard"}'
```

### Postman Collection
The API can be tested using Postman. Import the OpenAPI spec from:
`http://localhost:3001/api/docs.json`

### SDK Usage (if available)
```javascript
import { DashboardAPI } from '@enterprise-dashboard/sdk';

const api = new DashboardAPI({
  baseURL: 'http://localhost:3001/api',
  token: 'your_jwt_token'
});

// Create dashboard
const dashboard = await api.dashboards.create({
  title: 'My Dashboard',
  description: 'A sample dashboard'
});

// Get analytics
const analytics = await api.dashboards.getAnalytics(dashboard.id, {
  startDate: '2024-01-01',
  endDate: '2024-01-31'
});
```

## 🐛 Error Handling

### Common Error Codes
- `400` - Bad Request (validation errors)
- `401` - Unauthorized (authentication required)
- `403` - Forbidden (insufficient permissions)
- `404` - Not Found (resource doesn't exist)
- `409` - Conflict (duplicate resource)
- `429` - Too Many Requests (rate limited)
- `500` - Internal Server Error
- `503` - Service Unavailable

### Error Response Examples

#### Validation Error
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid input data",
    "statusCode": 400,
    "details": {
      "issues": [
        {
          "path": "email",
          "message": "Invalid email format",
          "code": "invalid_string"
        }
      ]
    }
  }
}
```

#### Authentication Error
```json
{
  "success": false,
  "error": {
    "code": "TOKEN_EXPIRED",
    "message": "Authentication token has expired",
    "statusCode": 401
  }
}
```

#### Rate Limit Error
```json
{
  "success": false,
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Too many requests, please try again later",
    "statusCode": 429
  }
}
```

## 📈 Monitoring & Analytics

### Health Checks
```bash
# System health
curl http://localhost:3001/health

# WebSocket health
curl http://localhost:3001/api/websocket/health

# Backup system health (Admin required)
curl -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:3001/api/backup/health
```

### Metrics Endpoints
- `GET /api/backup/stats` - Backup system statistics
- `GET /api/websocket/stats` - WebSocket server statistics
- `GET /api/users/{id}/stats` - User activity statistics

## 🔧 Configuration

### Environment Variables
Key configuration options:

```bash
# API Configuration
NODE_ENV=development
PORT=3001
API_BASE_URL=http://localhost:3001

# Authentication
JWT_SECRET=your-secret-key
JWT_EXPIRY=24h
BCRYPT_ROUNDS=12

# Database
DATABASE_URL=postgresql://user:password@localhost:5432/dashboard_db

# Redis
REDIS_URL=redis://localhost:6379

# WebSocket
WS_PORT=3002
WS_MAX_CONNECTIONS=1000

# Rate Limiting
RATE_LIMIT_WINDOW_MS=900000  # 15 minutes
RATE_LIMIT_MAX_REQUESTS=100

# Backup
BACKUP_ENABLED=true
BACKUP_DIR=./backups
BACKUP_RETENTION_DAYS=30
```

## 🤝 Support

### Documentation
- **Swagger UI**: `/api/docs` (development only)
- **API Reference**: This document
- **WebSocket Guide**: See WebSocket section above

### Common Issues

1. **Authentication Errors**
   - Ensure JWT token is valid and not expired
   - Include `Authorization: Bearer <token>` header
   - For state-changing operations, include CSRF token

2. **Rate Limiting**
   - Check rate limit headers in response
   - Implement exponential backoff
   - Use appropriate endpoints (don't poll unnecessarily)

3. **WebSocket Connection Issues**
   - Verify token is passed as query parameter
   - Check WebSocket server status at `/api/websocket/health`
   - Ensure proper error handling for connection failures

4. **Backup Operations**
   - Only admin users can perform backup operations
   - Super admin required for restore operations
   - Check disk space before creating backups

### Getting Help
- Check the interactive documentation at `/api/docs`
- Review error messages and status codes
- Monitor application logs for detailed error information
- Use health check endpoints to verify service status

## 📄 License

This API documentation is part of the Enterprise Dashboard Platform.
See the main project LICENSE file for details.

---

*This documentation is automatically generated from OpenAPI specifications and kept up to date with the API implementation.*