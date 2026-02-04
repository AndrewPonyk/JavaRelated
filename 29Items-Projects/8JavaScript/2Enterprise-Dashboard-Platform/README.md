# Enterprise Dashboard Platform

> A comprehensive analytics dashboard platform with drag-drop builder, real-time updates, and ML-powered anomaly detection.

[![CI/CD](https://github.com/your-org/enterprise-dashboard-platform/workflows/CI/CD%20Pipeline/badge.svg)](https://github.com/your-org/enterprise-dashboard-platform/actions)
[![codecov](https://codecov.io/gh/your-org/enterprise-dashboard-platform/branch/main/graph/badge.svg)](https://codecov.io/gh/your-org/enterprise-dashboard-platform)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## 🚀 Features

### Core Features
- **Drag & Drop Dashboard Builder** - Intuitive interface for creating custom dashboards
- **Real-time Data Updates** - Live data synchronization with WebSocket connections
- **Interactive Visualizations** - Powered by D3.js with 12+ chart types
- **ML Anomaly Detection** - TensorFlow.js-based client-side prediction and highlighting
- **Server-side Rendering** - Optimized for performance with progressive hydration
- **Offline-first Architecture** - Service worker caching for offline functionality

### Advanced Features
- **Multi-tenant Architecture** - Support for multiple organizations
- **Role-based Access Control** - Granular permissions and security
- **Data Source Integrations** - Connect to PostgreSQL, MongoDB, REST APIs, and more
- **Export Capabilities** - PDF, CSV, Excel, and image exports
- **Responsive Design** - Mobile-first approach with Tailwind CSS
- **Comprehensive Testing** - Unit, integration, and E2E tests with Playwright

## ✅ Implemented Functionality

### Authentication & Authorization
- **JWT Authentication** - Secure login/logout with access and refresh tokens
- **CSRF Protection** - Token-based CSRF protection for state-changing operations
- **Role-Based Access Control** - User roles: SUPER_ADMIN, ADMIN, MANAGER, USER, VIEWER
- **Session Management** - Secure session handling with Redis storage
- **Password Security** - Bcrypt hashing with configurable rounds

### User Management
- **User CRUD Operations** - Create, read, update, delete users (admin only)
- **User Profiles** - Extended user profiles with preferences
- **User Search** - Search users by name or email
- **Pagination & Filtering** - Server-side pagination with role/status filters

### Dashboard System
- **Dashboard CRUD** - Create, view, edit, delete dashboards
- **Dashboard Sharing** - Share dashboards with other users
- **Dashboard Templates** - Save dashboards as reusable templates
- **Sample Dashboards** - Pre-built Sales and Marketing dashboards

### Widget System
- **Widget Types** - Metric, Chart, Table, and custom widgets
- **Widget Positioning** - 12-column responsive grid layout
- **Widget Configuration** - Customizable settings per widget type

### Backend Infrastructure
- **RESTful API** - Express.js with async error handling
- **Database ORM** - Prisma with PostgreSQL
- **Caching Layer** - Redis caching with configurable TTL
- **Rate Limiting** - Request throttling per endpoint
- **Request Logging** - Winston logger with file rotation
- **Health Checks** - Database and Redis health monitoring
- **DataLoader** - Batched database queries for performance

### Frontend Features
- **React 18** - Modern React with hooks and concurrent features
- **TypeScript** - Full type safety across the codebase
- **TanStack Query** - Server state management with caching
- **Responsive Design** - Mobile-first with Tailwind CSS
- **Toast Notifications** - User feedback for actions
- **Error Boundaries** - Graceful error handling

### DevOps & Monitoring
- **Docker Compose** - Multi-container development environment
- **Prometheus** - Metrics collection
- **Grafana** - Monitoring dashboards
- **Database Backups** - Automated backup scripts

## ⚠️ **IMPORTANT NOTICE: Data Source Limitations**

**🚨 EXTERNAL DATA INTEGRATION IS NOT CURRENTLY IMPLEMENTED**

While this is an **impressive dashboard platform with flexible layouts, real-time features, and enterprise-grade security**, the external data source integration is a **major missing piece that would need significant development work to implement**.

**What Works ✅:**
- Complete dashboard creation and layout system
- Drag & drop widget positioning with 12-column responsive grid
- User management, authentication, and role-based permissions
- Real-time WebSocket updates and collaboration
- Sample dashboards with demo data
- Enterprise backup and monitoring systems

**What's Missing ❌:**
- **CSV file uploads** - No file parsing implementation
- **External database connections** - No MySQL/PostgreSQL connectors
- **REST API integration** - No external API data fetching
- **Google Sheets, Salesforce, etc.** - No SaaS integrations

**Current Data Sources:** Dashboards currently use static demo data or manually entered data only.

**For Production Use:** You would need to implement data source connectors, file upload services, and API integration services to connect to real data sources.

## 📊 Data Model

The application uses PostgreSQL with Prisma ORM. Below is the entity relationship overview:

### Core Entities

```
┌─────────────┐       ┌─────────────────┐       ┌────────────┐
│    User     │──────<│    Dashboard    │──────<│   Widget   │
└─────────────┘       └─────────────────┘       └────────────┘
      │                       │
      │                       │
      ▼                       ▼
┌─────────────┐       ┌─────────────────┐
│ UserProfile │       │ DashboardShare  │
└─────────────┘       └─────────────────┘
```

### Dashboard Storage

Dashboards are stored in the `dashboards` table with the following key fields:

| Field | Type | Description |
|-------|------|-------------|
| `id` | String (CUID) | Unique identifier |
| `title` | String | Dashboard name |
| `description` | String? | Optional description |
| `slug` | String? | URL-friendly identifier |
| `layout` | JSON | Widget positions and grid layout configuration |
| `settings` | JSON | Dashboard-level settings (theme, refresh rate, etc.) |
| `isPublic` | Boolean | Whether dashboard is publicly accessible |
| `isTemplate` | Boolean | Whether dashboard can be used as a template |
| `userId` | String | Owner's user ID |

### Widget Storage

Widgets are stored in the `widgets` table:

| Field | Type | Description |
|-------|------|-------------|
| `id` | String (CUID) | Unique identifier |
| `title` | String | Widget title |
| `type` | Enum | Widget type (CHART_LINE, CHART_BAR, TABLE, METRIC, etc.) |
| `config` | JSON | Widget-specific configuration (colors, axes, etc.) |
| `position` | JSON | Grid position `{x, y, w, h}` |
| `query` | String? | Data query (if applicable) |
| `dataSource` | String? | Data source identifier |
| `refreshRate` | Int? | Auto-refresh interval in seconds |
| `cachedData` | JSON? | Cached query results |
| `dashboardId` | String | Parent dashboard ID |

### Widget Types (Enum)

```
CHART_LINE, CHART_BAR, CHART_PIE, CHART_AREA, CHART_SCATTER,
TABLE, METRIC, TEXT, IMAGE, MAP, HEATMAP, GAUGE, CUSTOM
```

### User Roles (Enum)

```
SUPER_ADMIN, ADMIN, MANAGER, USER, VIEWER
```

### Share Permissions (Enum)

```
READ, WRITE, ADMIN
```

### Other Entities

| Entity | Table | Description |
|--------|-------|-------------|
| UserProfile | `user_profiles` | User preferences (theme, timezone, language) |
| DashboardShare | `dashboard_shares` | Dashboard sharing with permissions |
| DataConnection | `data_connections` | External data source configurations |
| SavedQuery | `saved_queries` | Saved queries for data connections |
| DashboardAnalytics | `dashboard_analytics` | View statistics and metrics |
| ActivityLog | `activity_logs` | User activity audit trail |
| CalendarEvent | `calendar_events` | Calendar/scheduling events |
| Notification | `notifications` | User notifications |
| MLModel | `ml_models` | Machine learning model configurations |
| Prediction | `predictions` | ML prediction results |

### Example: Dashboard Layout JSON

```json
{
  "layout": [
    { "i": "widget-1", "x": 0, "y": 0, "w": 6, "h": 4 },
    { "i": "widget-2", "x": 6, "y": 0, "w": 6, "h": 4 },
    { "i": "widget-3", "x": 0, "y": 4, "w": 12, "h": 6 }
  ],
  "settings": {
    "refreshInterval": 30,
    "theme": "light",
    "gridCols": 12
  }
}
```

### Example: Widget Config JSON

```json
{
  "config": {
    "chartType": "line",
    "xAxis": { "field": "date", "label": "Date" },
    "yAxis": { "field": "value", "label": "Revenue" },
    "colors": ["#3b82f6", "#10b981"],
    "showLegend": true,
    "showGrid": true
  },
  "position": { "x": 0, "y": 0, "w": 6, "h": 4 }
}
```

## 🛠 Tech Stack

### Frontend
- **React 18** - UI framework with concurrent features
- **TypeScript** - Type-safe development
- **Vite** - Fast build tool and development server
- **TanStack Query** - Server state management and caching
- **Shadcn/UI** - Modern, accessible component library
- **D3.js** - Data visualization and charting
- **TensorFlow.js** - Client-side machine learning

### Backend
- **Node.js** - Runtime environment
- **Express.js** - Web application framework
- **Prisma** - Type-safe database ORM
- **PostgreSQL** - Primary database
- **Redis** - Caching and session storage
- **DataLoader** - Efficient data batching

### DevOps & Tools
- **Azure Static Web Apps** - Hosting and deployment
- **GitHub Actions** - CI/CD pipeline
- **Docker** - Containerization
- **Playwright** - End-to-end testing
- **ESLint & Prettier** - Code quality and formatting

## 📋 Prerequisites

Before you begin, ensure you have the following installed:

- **Node.js** (v18.0.0 or higher)
- **npm** (v9.0.0 or higher)
- **PostgreSQL** (v15 or higher)
- **Redis** (v7 or higher)
- **Git**

### Optional (for Docker setup)
- **Docker** (v24 or higher)
- **Docker Compose** (v2.20 or higher)

## 🚀 Quick Start

### 1. Clone the Repository

```bash
git clone https://github.com/your-org/enterprise-dashboard-platform.git
cd enterprise-dashboard-platform
```

### 2. Install Dependencies

```bash
npm install
```

### 3. Environment Setup

```bash
cp .env.example .env.local
```

Edit `.env.local` with your configuration:

```bash
# Database
DATABASE_URL="postgresql://username:password@localhost:5432/dashboard_platform"

# Redis
REDIS_URL="redis://localhost:6379"

# JWT Secret (generate a secure key)
JWT_SECRET="your-super-secret-jwt-key-min-32-chars"

# API Configuration
VITE_API_URL="http://localhost:3001"
VITE_WS_URL="ws://localhost:3002"
```

### 4. Database Setup

```bash
# Generate Prisma client
npm run db:generate

# Run database migrations
npm run db:migrate

# Seed the database (optional)
npm run db:seed
```

### 5. Start Development Servers

```bash
# Start both frontend and backend
npm run dev

# Or start individually
npm run dev:frontend  # Frontend on http://localhost:3000
npm run dev:backend   # Backend on http://localhost:3001
```

### 6. Access the Application

- **Frontend**: http://localhost:3000
- **Backend API**: http://localhost:3001
- **Database Studio**: http://localhost:5555 (run `npm run db:studio`)

## 🐳 Docker Setup

For a containerized development environment:

```bash
# Build and start all services
npm run docker:up

# View logs
npm run docker:logs

# Stop all services
npm run docker:down
```

This will start:
- Frontend (port 3000)
- Backend (port 3001)
- PostgreSQL (port 5432)
- Redis (port 6379)
- Adminer (port 8080) - Database management
- Redis Commander (port 8081) - Redis management

## 📖 Documentation

Comprehensive documentation is available in the `/docs` directory:

- [**Project Plan**](./docs/PROJECT-PLAN.md) - Implementation roadmap and TODO list
- [**Architecture**](./docs/ARCHITECTURE.md) - System design and component interactions
- [**Technical Notes**](./docs/TECH-NOTES.md) - CI/CD, testing, and deployment strategies
- [**API Documentation**](./docs/API.md) - RESTful API endpoints and schemas
- [**Deployment Guide**](./docs/DEPLOYMENT.md) - Production deployment instructions

## 🧪 Testing

### Run All Tests
```bash
npm run test
```

### Test Types
```bash
# Unit tests
npm run test:frontend
npm run test:backend

# Integration tests
npm run test:integration

# End-to-end tests
npm run test:e2e

# Test coverage
npm run test:coverage
```

### Code Quality
```bash
# Linting
npm run lint
npm run lint:fix

# Type checking
npm run type-check

# Formatting
npm run format
npm run format:check
```

## 🚀 Deployment

### Staging Deployment
```bash
npm run deploy:staging
```

### Production Deployment
```bash
npm run deploy:production
```

The application is configured for Azure Static Web Apps with automatic deployments via GitHub Actions.

## 📁 Project Structure

```
enterprise-dashboard-platform/
├── docs/                     # Documentation
├── src/                      # Frontend source code
│   ├── components/           # React components
│   ├── pages/               # Page components
│   ├── hooks/               # Custom hooks
│   ├── services/            # API services
│   ├── stores/              # State management
│   ├── types/               # TypeScript types
│   └── utils/               # Utility functions
├── backend/                 # Backend API
│   ├── src/                 # Source code
│   ├── prisma/              # Database schema
│   └── tests/               # Backend tests
├── shared/                  # Shared types and utilities
├── tests/                   # Test files
│   ├── unit/                # Unit tests
│   ├── integration/         # Integration tests
│   └── e2e/                 # End-to-end tests
├── .github/                 # GitHub workflows
├── config/                  # Configuration files
├── docker/                  # Docker configurations
└── scripts/                 # Build and utility scripts
```

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guidelines](./CONTRIBUTING.md) for details.

### Development Workflow

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Run tests and linting (`npm run test && npm run lint`)
5. Commit your changes (`git commit -m 'Add amazing feature'`)
6. Push to the branch (`git push origin feature/amazing-feature`)
7. Open a Pull Request

### Commit Convention

We use [Conventional Commits](https://conventionalcommits.org/):

```
feat(dashboard): add drag-and-drop functionality
fix(api): resolve authentication middleware issue
docs: update README with Docker setup
chore: upgrade dependencies
```

## 📊 Performance

### Key Metrics
- **Page Load Time**: < 2 seconds
- **API Response Time**: < 500ms
- **First Contentful Paint**: < 1.5 seconds
- **Lighthouse Score**: > 90

### Optimization Features
- Code splitting and lazy loading
- Service Worker caching
- Database query optimization with DataLoader
- Redis caching layer
- CDN integration for static assets

## 🔒 Security

- **Authentication**: JWT-based with refresh token rotation
- **Authorization**: Role-based access control (RBAC)
- **Data Protection**: Encryption at rest and in transit
- **Input Validation**: Comprehensive request validation with Zod
- **Rate Limiting**: API request throttling
- **CORS**: Configured for secure cross-origin requests

## 🐛 Troubleshooting

### Common Issues

**Database Connection Issues**
```bash
# Check PostgreSQL status
pg_isready -h localhost -p 5432

# Reset database
npm run db:reset
```

**Redis Connection Issues**
```bash
# Check Redis status
redis-cli ping

# Clear Redis cache
redis-cli flushall
```

**Build Issues**
```bash
# Clear all caches and reinstall
npm run clean
npm install
```

### Prisma / Database Issues

**"relation 'User' does not exist" or "relation 'users' does not exist"**

This error occurs when the database tables haven't been created or were dropped. The Prisma schema maps models to lowercase table names (e.g., `User` model -> `users` table).

**Solution:**

```bash
# If using Docker:
docker compose exec backend npx prisma db push --force-reset
docker compose exec backend npm run db:seed

# If running locally:
cd backend
npx prisma db push --force-reset
npm run db:seed
```

**Database tables lost after Docker restart**

PostgreSQL data is persisted in a Docker volume. If the volume was removed, you'll need to recreate the schema:

```bash
# Recreate tables and seed data
docker compose exec backend npx prisma db push
docker compose exec backend npm run db:seed
```

**Health check errors with "User" table**

If you see errors like `SELECT COUNT(*) FROM "User"` failing, this is a case-sensitivity issue. PostgreSQL treats quoted identifiers as case-sensitive. The fix has been applied in `backend/src/utils/performance/databaseOptimizer.ts` to use the correct lowercase table name `"users"`.

**Prisma generate issues**

```bash
# Regenerate Prisma client
docker compose exec backend npx prisma generate

# Or locally:
cd backend && npx prisma generate
```

## 🐳 Docker Development Setup

### Environment Configuration

For development with Docker, ensure your `.env` file has:

```bash
NODE_ENV=development
FRONTEND_INTERNAL_PORT=3000  # Use 3000 for dev (Vite), 80 for prod (nginx)
```

### Port Mapping

| Service | Internal Port | External Port | Description |
|---------|--------------|---------------|-------------|
| Frontend | 3000 (dev) / 80 (prod) | 3000 | Vite dev server or nginx |
| Backend | 3001 | 3001 | Express API |
| PostgreSQL | 5432 | 5432 | Database |
| Redis | 6379 | 6379 | Cache |
| Grafana | 3000 | 3002 | Monitoring |
| Prometheus | 9090 | 9090 | Metrics |

### Full Rebuild

```bash
# Stop all containers and rebuild from scratch
docker compose down --volumes --remove-orphans
docker compose build --no-cache
docker compose up -d

# Then initialize the database
docker compose exec backend npx prisma db push
docker compose exec backend npm run db:seed
```

### Default Credentials

**Application Users (after seeding):**
- Admin: `admin@dashboard.com` / `Admin123!`
- Demo: `demo@dashboard.com` / `Demo123!`

**PostgreSQL:**
- Host: `localhost` (or `postgres` from within Docker)
- Port: `5432`
- Database: `enterprise_dashboard`
- User: `postgres`
- Password: `password123`

**Redis:**
- Host: `localhost` (or `redis` from within Docker)
- Port: `6379`
- Password: `redis123`

### Getting Help

- 📖 Check the [documentation](./docs/)
- 🐛 [Report bugs](https://github.com/your-org/enterprise-dashboard-platform/issues)
- 💬 [Join discussions](https://github.com/your-org/enterprise-dashboard-platform/discussions)

### Quick Fixes Reference

| Issue | Quick Fix |
|-------|-----------|
| Database tables missing | `docker compose exec backend npx prisma db push` |
| Need seed data | `docker compose exec backend npm run db:seed` |
| Frontend not loading | Check `FRONTEND_INTERNAL_PORT=3000` in `.env` |
| API proxy not working | Restart frontend: `docker compose restart frontend` |
| Auth token expired | Log out and log back in |
| Users endpoint timeout | Increase timeout in `apiClient.ts` or check DB connection |

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](./LICENSE) file for details.

## 🙏 Acknowledgments

- [React](https://reactjs.org/) - UI framework
- [D3.js](https://d3js.org/) - Data visualization
- [Prisma](https://prisma.io/) - Database toolkit
- [TanStack Query](https://tanstack.com/query) - Data fetching
- [Shadcn/UI](https://ui.shadcn.com/) - Component library
- [Azure](https://azure.microsoft.com/) - Cloud hosting

---

**Built with ❤️ by the Enterprise Dashboard Team**