import swaggerJsdoc from 'swagger-jsdoc';
import { config } from '@/config/environment.js';
const swaggerDefinition = {
    openapi: '3.0.0',
    info: {
        title: 'Enterprise Dashboard Platform API',
        version: config.app.version,
        description: `
# Enterprise Dashboard Platform API

A comprehensive REST API for managing enterprise dashboards, users, analytics, and more.

## Features

- **Authentication & Authorization**: JWT-based auth with role-based access control
- **Dashboard Management**: Create, update, share, and analyze interactive dashboards
- **Real-time Updates**: WebSocket support for live data synchronization
- **User Management**: Complete user lifecycle with profiles and permissions
- **Security**: CSRF protection, rate limiting, input validation
- **Backup & Recovery**: Automated database backups with restoration capabilities
- **Analytics**: Comprehensive dashboard and user analytics
- **Export**: Multi-format dashboard exports (PDF, PNG, CSV, JSON)

## Security

This API uses JWT (JSON Web Tokens) for authentication. Include the token in the Authorization header:

\`\`\`
Authorization: Bearer <your-jwt-token>
\`\`\`

For state-changing operations, CSRF tokens are required. Get a token from \`/api/auth/csrf-token\` and include it in the \`X-CSRF-Token\` header.

## Rate Limiting

Different endpoints have different rate limits:
- Authentication: 10 requests per 15 minutes
- Dashboard operations: 200 requests per 15 minutes
- User operations: 100 requests per 15 minutes
- Global: 100 requests per 15 minutes

## Error Handling

All endpoints return standardized error responses:

\`\`\`json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "Human readable message",
    "statusCode": 400,
    "details": { /* Additional error context */ }
  },
  "requestId": "unique-request-id",
  "timestamp": "2024-01-01T00:00:00.000Z"
}
\`\`\`

## Pagination

List endpoints support pagination with these query parameters:
- \`page\`: Page number (default: 1)
- \`limit\`: Items per page (default: 10, max: 100)
- \`sortBy\`: Field to sort by
- \`sortOrder\`: 'asc' or 'desc'
- \`search\`: Search query string
    `,
        contact: {
            name: 'API Support',
            email: 'api-support@enterprise-dashboard.com',
        },
        license: {
            name: 'MIT',
            url: 'https://opensource.org/licenses/MIT',
        },
    },
    servers: [
        {
            url: config.app.apiBaseUrl,
            description: `${config.app.env} server`,
        },
    ],
    components: {
        securitySchemes: {
            BearerAuth: {
                type: 'http',
                scheme: 'bearer',
                bearerFormat: 'JWT',
                description: 'Enter JWT token obtained from login endpoint',
            },
            CSRFToken: {
                type: 'apiKey',
                in: 'header',
                name: 'X-CSRF-Token',
                description: 'CSRF protection token for state-changing operations',
            },
        },
        schemas: {
            SuccessResponse: {
                type: 'object',
                properties: {
                    success: { type: 'boolean', example: true },
                    data: { type: 'object' },
                    message: { type: 'string', example: 'Operation completed successfully' },
                    timestamp: { type: 'string', format: 'date-time' },
                },
            },
            ErrorResponse: {
                type: 'object',
                properties: {
                    success: { type: 'boolean', example: false },
                    error: {
                        type: 'object',
                        properties: {
                            code: { type: 'string', example: 'VALIDATION_ERROR' },
                            message: { type: 'string', example: 'Invalid input data' },
                            statusCode: { type: 'integer', example: 400 },
                            details: { type: 'object' },
                        },
                    },
                    requestId: { type: 'string', example: 'req-12345' },
                    timestamp: { type: 'string', format: 'date-time' },
                },
            },
            PaginatedResponse: {
                type: 'object',
                properties: {
                    success: { type: 'boolean', example: true },
                    data: {
                        type: 'object',
                        properties: {
                            items: { type: 'array', items: {} },
                            totalItems: { type: 'integer', example: 100 },
                            totalPages: { type: 'integer', example: 10 },
                            currentPage: { type: 'integer', example: 1 },
                            limit: { type: 'integer', example: 10 },
                            hasNext: { type: 'boolean', example: true },
                            hasPrev: { type: 'boolean', example: false },
                        },
                    },
                    message: { type: 'string', example: 'Data retrieved successfully' },
                },
            },
            User: {
                type: 'object',
                properties: {
                    id: { type: 'string', example: 'usr_123456789' },
                    email: { type: 'string', format: 'email', example: 'user@example.com' },
                    name: { type: 'string', example: 'John Doe' },
                    role: { type: 'string', enum: ['USER', 'ADMIN', 'SUPER_ADMIN'], example: 'USER' },
                    isActive: { type: 'boolean', example: true },
                    avatar: { type: 'string', nullable: true, example: 'https://example.com/avatar.jpg' },
                    lastLoginAt: { type: 'string', format: 'date-time', nullable: true },
                    createdAt: { type: 'string', format: 'date-time' },
                    updatedAt: { type: 'string', format: 'date-time' },
                },
            },
            UserProfile: {
                type: 'object',
                properties: {
                    bio: { type: 'string', nullable: true, example: 'Dashboard enthusiast' },
                    company: { type: 'string', nullable: true, example: 'Acme Corp' },
                    location: { type: 'string', nullable: true, example: 'New York, NY' },
                    website: { type: 'string', nullable: true, example: 'https://johndoe.com' },
                    timezone: { type: 'string', example: 'America/New_York' },
                    theme: { type: 'string', enum: ['light', 'dark', 'auto'], example: 'dark' },
                    language: { type: 'string', example: 'en' },
                },
            },
            LoginRequest: {
                type: 'object',
                required: ['email', 'password'],
                properties: {
                    email: { type: 'string', format: 'email', example: 'user@example.com' },
                    password: { type: 'string', minLength: 6, example: 'password123' },
                    rememberMe: { type: 'boolean', example: false },
                },
            },
            LoginResponse: {
                type: 'object',
                properties: {
                    success: { type: 'boolean', example: true },
                    data: {
                        type: 'object',
                        properties: {
                            user: { $ref: '#/components/schemas/User' },
                            accessToken: { type: 'string', example: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...' },
                            refreshToken: { type: 'string', example: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...' },
                            expiresIn: { type: 'integer', example: 86400 },
                        },
                    },
                    message: { type: 'string', example: 'Login successful' },
                },
            },
            RegisterRequest: {
                type: 'object',
                required: ['email', 'password', 'name'],
                properties: {
                    email: { type: 'string', format: 'email', example: 'user@example.com' },
                    password: { type: 'string', minLength: 6, example: 'password123' },
                    name: { type: 'string', minLength: 2, example: 'John Doe' },
                    company: { type: 'string', example: 'Acme Corp' },
                },
            },
            Dashboard: {
                type: 'object',
                properties: {
                    id: { type: 'string', example: 'dash_123456789' },
                    title: { type: 'string', example: 'Sales Analytics Dashboard' },
                    description: { type: 'string', nullable: true, example: 'Monthly sales performance metrics' },
                    isPublic: { type: 'boolean', example: false },
                    isTemplate: { type: 'boolean', example: false },
                    layout: {
                        type: 'object',
                        properties: {
                            rows: { type: 'integer', example: 4 },
                            cols: { type: 'integer', example: 12 },
                            gap: { type: 'integer', example: 16 },
                        },
                    },
                    widgets: {
                        type: 'array',
                        items: { $ref: '#/components/schemas/Widget' },
                    },
                    tags: {
                        type: 'array',
                        items: { type: 'string' },
                        example: ['sales', 'analytics', 'monthly'],
                    },
                    ownerId: { type: 'string', example: 'usr_123456789' },
                    owner: { $ref: '#/components/schemas/User' },
                    viewCount: { type: 'integer', example: 125 },
                    lastViewedAt: { type: 'string', format: 'date-time', nullable: true },
                    createdAt: { type: 'string', format: 'date-time' },
                    updatedAt: { type: 'string', format: 'date-time' },
                },
            },
            Widget: {
                type: 'object',
                properties: {
                    id: { type: 'string', example: 'wgt_123456789' },
                    type: { type: 'string', enum: ['chart', 'table', 'kpi', 'text'], example: 'chart' },
                    title: { type: 'string', example: 'Monthly Revenue' },
                    position: {
                        type: 'object',
                        properties: {
                            x: { type: 'integer', example: 0 },
                            y: { type: 'integer', example: 0 },
                            w: { type: 'integer', example: 6 },
                            h: { type: 'integer', example: 4 },
                        },
                    },
                    config: {
                        type: 'object',
                        example: {
                            chartType: 'line',
                            dataSource: 'sales_api',
                            refreshInterval: 300000,
                        },
                    },
                    data: { type: 'object', nullable: true },
                    isVisible: { type: 'boolean', example: true },
                    createdAt: { type: 'string', format: 'date-time' },
                    updatedAt: { type: 'string', format: 'date-time' },
                },
            },
            CreateDashboardRequest: {
                type: 'object',
                required: ['title'],
                properties: {
                    title: { type: 'string', minLength: 1, maxLength: 255, example: 'My New Dashboard' },
                    description: { type: 'string', maxLength: 1000, example: 'Dashboard description' },
                    isPublic: { type: 'boolean', example: false },
                    isTemplate: { type: 'boolean', example: false },
                    layout: {
                        type: 'object',
                        properties: {
                            rows: { type: 'integer', minimum: 1, maximum: 20, example: 4 },
                            cols: { type: 'integer', minimum: 1, maximum: 24, example: 12 },
                            gap: { type: 'integer', minimum: 0, maximum: 32, example: 16 },
                        },
                    },
                    tags: {
                        type: 'array',
                        items: { type: 'string' },
                        maxItems: 10,
                        example: ['analytics', 'sales'],
                    },
                },
            },
            WSMessage: {
                type: 'object',
                properties: {
                    type: { type: 'string', enum: ['dashboard_update', 'widget_update', 'user_activity'], example: 'dashboard_update' },
                    topic: { type: 'string', example: 'dashboard:dash_123456789' },
                    data: { type: 'object' },
                    timestamp: { type: 'string', format: 'date-time' },
                    userId: { type: 'string', example: 'usr_123456789' },
                },
            },
            BackupMetadata: {
                type: 'object',
                properties: {
                    filename: { type: 'string', example: 'backup-2024-01-15T02-00-00-000Z-daily.sql.gz' },
                    timestamp: { type: 'string', format: 'date-time' },
                    size: { type: 'integer', example: 52428800 },
                    compressed: { type: 'boolean', example: true },
                    checksum: { type: 'string', example: 'sha256:abc123...' },
                    databaseSize: { type: 'integer', example: 104857600 },
                    tables: { type: 'array', items: { type: 'string' }, example: ['users', 'dashboards'] },
                    version: { type: 'string', example: 'PostgreSQL 15.5' },
                    duration: { type: 'integer', example: 45000 },
                },
            },
            HealthResponse: {
                type: 'object',
                properties: {
                    success: { type: 'boolean', example: true },
                    data: {
                        type: 'object',
                        properties: {
                            status: { type: 'string', enum: ['healthy', 'unhealthy'], example: 'healthy' },
                            timestamp: { type: 'string', format: 'date-time' },
                            uptime: { type: 'number', example: 12345.678 },
                            environment: { type: 'string', example: 'production' },
                            version: { type: 'string', example: '1.0.0' },
                            services: {
                                type: 'object',
                                properties: {
                                    database: { type: 'string', enum: ['healthy', 'unhealthy'], example: 'healthy' },
                                    redis: { type: 'string', enum: ['healthy', 'unhealthy'], example: 'healthy' },
                                },
                            },
                            memory: {
                                type: 'object',
                                properties: {
                                    rss: { type: 'integer', example: 123456789 },
                                    heapTotal: { type: 'integer', example: 987654321 },
                                    heapUsed: { type: 'integer', example: 456789123 },
                                },
                            },
                            pid: { type: 'integer', example: 12345 },
                        },
                    },
                    message: { type: 'string', example: 'Service is healthy' },
                },
            },
        },
    },
    tags: [
        {
            name: 'Authentication',
            description: 'User authentication and authorization endpoints',
        },
        {
            name: 'Users',
            description: 'User management and profile operations',
        },
        {
            name: 'Dashboards',
            description: 'Dashboard creation, management, and analytics',
        },
        {
            name: 'WebSocket',
            description: 'Real-time communication and updates',
        },
        {
            name: 'Backup',
            description: 'Database backup and restoration operations',
        },
        {
            name: 'System',
            description: 'System health and monitoring endpoints',
        },
    ],
};
const options = {
    definition: swaggerDefinition,
    apis: [
        './src/routes/*.ts',
        './src/controllers/**/*.ts',
        './src/docs/swagger/*.yaml',
    ],
};
export const swaggerSpec = swaggerJsdoc(options);
export default swaggerSpec;
//# sourceMappingURL=swagger.js.map