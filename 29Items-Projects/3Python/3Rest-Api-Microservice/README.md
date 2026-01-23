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

1. **Create migrations folder** (first time only)
   ```bash
   mkdir migrations
   ```

2. **Build and start database**
   ```bash
   docker-compose build
   docker-compose up db -d
   ```

3. **Initialize database migrations**
   ```bash
   docker-compose run --rm api flask db init
   docker-compose run --rm api flask db migrate -m "Initial migration"
   docker-compose run --rm api flask db upgrade
   ```

4. **Start all services**
   ```bash
   docker-compose up
   ```

5. **(Optional) Seed sample data**
   ```bash
   docker-compose exec api python scripts/seed_db.py
   ```

Access:
- Dashboard: http://localhost:5000
- API: http://localhost:5000/api
- Swagger UI: http://localhost:5000/apidocs
- phpMyAdmin: http://localhost:9090 (with `--profile tools`)

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

## Sentiment Analysis

The API includes NLP-powered sentiment analysis using Hugging Face's DistilBERT model.

### How It Works

Customer notes are analyzed and assigned a sentiment score:

| Score | Meaning |
|-------|---------|
| 0-30% | Negative sentiment |
| 30-70% | Neutral sentiment |
| 70-100% | Positive sentiment |

### Usage

**Analyze a customer's notes:**
```bash
curl -X POST http://localhost:5000/api/nlp/customer/1/analyze
```

**Analyze arbitrary text:**
```bash
curl -X POST http://localhost:5000/api/nlp/sentiment \
  -H "Content-Type: application/json" \
  -d '{"text": "Great service, very satisfied!"}'
```

### Example Notes

**Positive (70-100%):**
> Excellent customer! Always pays on time and refers new clients. Very satisfied with our services.

**Neutral (30-70%):**
> Standard account, regular monthly orders. No special requirements noted.

**Negative (0-30%):**
> Multiple complaints about delivery delays. Threatened to cancel subscription.

### Dashboard Usage

Each customer row in the dashboard has an **Analyze Sentiment** button (chat bubble icon). Click it to:

1. Send the customer's notes to the NLP service
2. Analyze sentiment using DistilBERT model
3. Automatically update the customer's sentiment score
4. Display the result as a color-coded progress bar (red → yellow → green)

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
