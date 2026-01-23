# Customer Management REST API

A Flask-based REST API for customer management with CRUD operations, search, analytics, and NLP capabilities using Hugging Face transformers.

## Features

- **Customer CRUD**: Create, read, update, delete customers
- **Search**: Full-text search and advanced filtering
- **Analytics**: Customer statistics and aggregation
- **NLP**: Sentiment analysis using Hugging Face transformers
- **API Documentation**: Swagger UI at `/apidocs`
- **Dashboard**: Bootstrap-based web interface

## Tech Stack

- **Framework**: Flask 3.x
- **ORM**: SQLAlchemy 2.x
- **Database**: MySQL 8.x
- **NLP**: Hugging Face Transformers
- **Testing**: pytest
- **CI/CD**: GitHub Actions
- **Deployment**: Railway

## Quick Start

### Prerequisites

- Python 3.12+
- MySQL 8.0+
- (Optional) Docker & Docker Compose

### Local Development

1. **Clone and setup environment**
   ```bash
   git clone <repository-url>
   cd customer-api
   python -m venv venv
   source venv/bin/activate  # On Windows: venv\Scripts\activate
   ```

2. **Install dependencies**
   ```bash
   pip install -r requirements.txt
   pip install -r requirements-dev.txt
   ```

3. **Configure environment**
   ```bash
   cp .env.example .env
   # Edit .env with your database credentials
   ```

4. **Initialize database**
   ```bash
   flask db upgrade
   python scripts/seed_db.py  # Optional: add sample data
   ```

5. **Run the application**
   ```bash
   python run.py
   ```

6. **Access the API**
   - API: http://localhost:5000/api
   - Swagger UI: http://localhost:5000/apidocs
   - Dashboard: http://localhost:5000

### Docker Development

```bash
docker-compose up -d
```

Access:
- API: http://localhost:5000
- phpMyAdmin: http://localhost:8080 (with `--profile tools`)

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/` | API welcome message |
| GET | `/api/health` | Health check |
| GET | `/api/customers/` | List customers (paginated) |
| GET | `/api/customers/{id}` | Get customer by ID |
| POST | `/api/customers/` | Create customer |
| PUT | `/api/customers/{id}` | Update customer |
| DELETE | `/api/customers/{id}` | Delete customer |
| GET | `/api/search/` | Search customers |
| POST | `/api/search/advanced` | Advanced search |
| GET | `/api/analytics/summary` | Get statistics |
| POST | `/api/nlp/sentiment` | Analyze text sentiment |
| POST | `/api/nlp/customer/{id}/analyze` | Analyze customer notes |

## Testing

```bash
# Run all tests
pytest

# Run with coverage
pytest --cov=app --cov-report=html

# Run specific test file
pytest tests/unit/test_customer_service.py -v
```

## Project Structure

```
├── app/
│   ├── __init__.py          # App factory
│   ├── config.py            # Configuration
│   ├── extensions.py        # Flask extensions
│   ├── api/                 # API endpoints
│   ├── models/              # Database models
│   ├── services/            # Business logic
│   ├── schemas/             # Serialization
│   ├── utils/               # Utilities
│   ├── templates/           # HTML templates
│   └── static/              # CSS, JS files
├── tests/                   # Test suite
├── migrations/              # Database migrations
├── scripts/                 # Utility scripts
├── docs/                    # Documentation
└── .github/workflows/       # CI/CD pipelines
```

## Configuration

Environment variables (see `.env.example`):

| Variable | Description | Default |
|----------|-------------|---------|
| `FLASK_ENV` | Environment | development |
| `DATABASE_URL` | MySQL connection string | - |
| `SECRET_KEY` | Flask secret key | - |
| `HF_MODEL_NAME` | Hugging Face model | distilbert-base-uncased-finetuned-sst-2-english |
| `API_KEY` | API authentication key | - |

## Deployment

### Railway

1. Connect GitHub repository to Railway
2. Set environment variables in Railway dashboard
3. Deploy automatically on push to `main`

See `.github/workflows/deploy.yml` for CI/CD configuration.

## Documentation

- [Project Plan](docs/PROJECT-PLAN.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Technical Notes](docs/TECH-NOTES.md)

## License

MIT
