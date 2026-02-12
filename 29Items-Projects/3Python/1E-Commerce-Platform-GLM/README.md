# E-Commerce Platform

Full-stack e-commerce platform built with Django, Celery, PostgreSQL, Redis, Elasticsearch, React, TypeScript, and TailwindCSS.

## Architecture

- **Backend**: Django 5.x with DRF
- **Frontend**: React 18 + TypeScript + Vite
- **Database**: PostgreSQL 15
- **Cache**: Redis 7
- **Search**: Elasticsearch 8
- **Async Tasks**: Celery
- **Deployment**: AWS ECS with Terraform
- **CI/CD**: GitHub Actions

## Quick Start

### Prerequisites

- Python 3.12+
- Node.js 20+
- Docker & Docker Compose
- PostgreSQL 15+
- Redis 7+
- Elasticsearch 8

### Development Setup

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd 1E-Commerce-Platform-GLM
   ```

2. **Configure environment**
   ```bash
   cp .env.example .env
   # Edit .env with your settings
   ```

3. **Start services with Docker Compose**
   ```bash
   docker-compose -f docker/docker-compose.yml up -d
   ```

4. **Run backend migrations**
   ```bash
   cd backend
   python manage.py migrate
   python manage.py createsuperuser
   python manage.py runserver
   ```

5. **Start frontend**
   ```bash
   cd frontend
   npm install
   npm run dev
   ```

6. **Access the application**
   - Frontend: http://localhost:3000
   - Backend API: http://localhost:8000/api/v1/
   - API Documentation: http://localhost:8000/api/docs/

## Project Structure

```
1E-Commerce-Platform-GLM/
├── backend/              # Django Backend
│   ├── api/             # REST API layer
│   ├── core/            # Core models & config
│   ├── tasks/           # Celery tasks
│   └── requirements.txt
├── frontend/            # React Frontend
│   ├── src/
│   │   ├── components/
│   │   ├── pages/
│   │   ├── services/
│   │   └── store/
│   └── package.json
├── infrastructure/      # Terraform config
│   └── terraform/
├── docker/              # Docker files
├── tests/               # Test suite
└── docs/                # Documentation
```

## Available Scripts

### Backend
```bash
cd backend
python manage.py runserver      # Start dev server
python manage.py migrate         # Run migrations
python manage.py createsuperuser # Create admin user
python manage.py shell           # Django shell
pytest                           # Run tests
```

### Frontend
```bash
cd frontend
npm run dev        # Start dev server
npm run build      # Build for production
npm run lint       # Run ESLint
npm run type-check # Run TypeScript check
npm run test       # Run tests
```

### Docker
```bash
docker-compose -f docker/docker-compose.yml up -d    # Start all services
docker-compose -f docker/docker-compose.yml down     # Stop all services
docker-compose -f docker/docker-compose.yml logs -f  # View logs
```

## API Documentation

- Swagger UI: `/api/docs/`
- ReDoc: `/api/redoc/`
- OpenAPI Schema: `/api/schema/`

## Features

- **User Authentication**: JWT-based auth with refresh tokens
- **Product Catalog**: Search, filter, and pagination
- **Shopping Cart**: Add/remove items, quantity management
- **Checkout Process**: Multiple payment methods (Stripe, PayPal, COD)
- **Order Management**: Order tracking, history, and status updates
- **Vendor Dashboard**: Product and sales management
- **Search**: Elasticsearch-powered product search
- **Recommendations**: Collaborative filtering for product suggestions
- **Email Notifications**: Order confirmations, shipping updates

## Deployment

See [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) for detailed deployment instructions.

### Terraform

```bash
cd infrastructure/terraform/environments/production
terraform init
terraform plan
terraform apply
```

## Testing

```bash
# Backend tests
pytest backend/tests/unit/           # Unit tests
pytest backend/tests/integration/    # Integration tests

# Frontend tests
npm run test:unit                    # Unit tests
npm run test:e2e                     # E2E tests
```

## Contributing

1. Create a feature branch
2. Make your changes
3. Run tests and linting
4. Submit a pull request

## License

MIT License - see LICENSE file for details.
