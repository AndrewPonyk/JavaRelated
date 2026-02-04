# 🚀 Enterprise Dashboard Platform - Deployment Guide

## Quick Start

### Prerequisites
- Docker and Docker Compose
- Node.js 18+ (for local development)
- Git

### 🐳 Docker Deployment (Recommended)

1. **Clone and start the application:**
   ```bash
   git clone <repository-url>
   cd enterprise-dashboard-platform
   docker-compose up -d
   ```

2. **Access the application:**
   - Frontend: http://localhost:3000
   - Backend API: http://localhost:3001
   - Prometheus: http://localhost:9090
   - Grafana: http://localhost:3001 (admin/admin)

3. **Default credentials:**
   - Email: `admin@example.com`
   - Password: `admin123`

### 🛠️ Local Development

1. **Install dependencies:**
   ```bash
   npm install
   cd backend && npm install
   ```

2. **Set up environment:**
   ```bash
   cp .env.example .env
   cp backend/.env.example backend/.env
   ```

3. **Start services:**
   ```bash
   # Start database and Redis
   docker-compose up postgres redis -d

   # Run migrations
   npm run db:migrate

   # Start development servers
   npm run dev
   ```

## 🧪 Testing

### Unit Tests
```bash
npm run test                # Run all tests
npm run test:coverage      # Run with coverage
npm run test:frontend      # Frontend tests only
npm run test:backend       # Backend tests only
```

### E2E Tests
```bash
npm run test:e2e           # Run Playwright tests
```

### Test Environment
```bash
docker-compose -f docker-compose.test.yml up -d
npm run test:e2e
```

## 🏗️ Build & Deploy

### Development Build
```bash
npm run build
```

### Production Deployment

#### Staging
```bash
npm run deploy:staging
```

#### Production
```bash
npm run deploy:production
```

## 📊 Monitoring & Observability

### Prometheus Metrics
- Application metrics: `http://localhost:3001/api/metrics`
- System metrics available in Prometheus dashboard
- Custom alerting rules configured

### Grafana Dashboards
- Pre-configured dashboards for application monitoring
- System performance metrics
- Business intelligence metrics

### Health Checks
- Frontend: `http://localhost:3000/health`
- Backend: `http://localhost:3001/health`
- API: `http://localhost:3001/api/health`

## 🔧 Configuration

### Environment Variables

#### Frontend (.env)
```bash
VITE_API_BASE_URL=http://localhost:3001/api
VITE_WS_URL=ws://localhost:3001
```

#### Backend (backend/.env)
```bash
NODE_ENV=development
PORT=3001
DATABASE_URL=postgresql://postgres:password@localhost:5432/enterprise_dashboard
REDIS_URL=redis://localhost:6379
JWT_SECRET=your-jwt-secret
JWT_REFRESH_SECRET=your-refresh-secret
```

## 📁 Project Structure

```
enterprise-dashboard-platform/
├── src/                      # Frontend source code
│   ├── components/           # React components
│   ├── pages/               # Page components
│   ├── hooks/               # Custom hooks
│   ├── services/            # API and ML services
│   ├── stores/              # State management
│   ├── lib/                 # Utilities and config
│   └── test/                # Test utilities
├── backend/                 # Backend source code
│   ├── src/                 # Node.js/Express API
│   ├── prisma/              # Database schema
│   ├── docker/              # Docker configs
│   └── scripts/             # Utility scripts
├── e2e/                     # End-to-end tests
├── monitoring/              # Prometheus config
├── nginx/                   # Nginx configuration
├── scripts/                 # Deployment scripts
└── docker-compose*.yml      # Container orchestration
```

## 🚨 Troubleshooting

### Common Issues

1. **Port conflicts:**
   ```bash
   docker-compose down
   docker-compose up -d
   ```

2. **Database connection issues:**
   ```bash
   docker-compose restart postgres
   npm run db:migrate
   ```

3. **Permission issues on scripts:**
   ```bash
   chmod +x scripts/*.sh
   ```

### Logs
```bash
# Application logs
docker-compose logs -f backend
docker-compose logs -f frontend

# All services
docker-compose logs -f
```

## 📚 Features Implemented

### ✅ Core Features
- [x] Full-stack authentication with JWT
- [x] Drag-and-drop dashboard builder
- [x] Real-time data visualization
- [x] Responsive design system
- [x] Role-based access control

### ✅ Advanced Features
- [x] ML-powered anomaly detection
- [x] Predictive analytics
- [x] AI insights generation
- [x] Real-time WebSocket updates
- [x] Advanced charting with D3.js and Recharts

### ✅ Infrastructure
- [x] Docker containerization
- [x] CI/CD pipeline (GitHub Actions)
- [x] Monitoring & alerting (Prometheus/Grafana)
- [x] Comprehensive testing suite
- [x] Production deployment scripts

### ✅ Performance & Security
- [x] Redis caching
- [x] Database optimization
- [x] Security best practices
- [x] Rate limiting
- [x] CORS configuration

## 🎯 Next Steps

This is a production-ready enterprise dashboard platform. Consider:

1. **Customization**: Modify components and styling to match your brand
2. **Data Sources**: Connect to your specific APIs and databases
3. **ML Models**: Train custom models for your specific use cases
4. **Scaling**: Configure horizontal scaling for high-traffic scenarios
5. **Security**: Review and adjust security policies for your environment

## 📞 Support

For issues and questions:
1. Check the troubleshooting section
2. Review application logs
3. Consult the GitHub repository issues

---

**Enterprise Dashboard Platform** - Built with ❤️ using React, Node.js, TypeScript, and modern DevOps practices.