"""
Pytest Configuration and Fixtures

|su:62) TEST FIXTURES - Shared setup code for tests. conftest.py is auto-discovered by pytest.
        Fixtures provide clean app/db/client for each test.

Provides shared fixtures for all tests including:
- Flask app with test configuration
- Database session with automatic cleanup
- Test client for API testing
- Sample data factories
"""
import pytest
from app import create_app
from app.extensions import db
from app.models.customer import Customer


# |su:63) APP FIXTURE - Creates Flask app with 'testing' config (uses SQLite in-memory)
@pytest.fixture(scope='function')
def app():
    """
    Create application for testing.

    Function-scoped to ensure clean database for each test.
    """
    app = create_app('testing')

    # |su:64) CONTEXT & CLEANUP - Create tables before test, drop after. yield = test runs here
    with app.app_context():
        db.create_all()
        yield app
        db.session.remove()
        db.drop_all()


# |su:65) TEST CLIENT - Flask's test client for making HTTP requests without running server
@pytest.fixture(scope='function')
def client(app):
    """
    Create test client.

    Function-scoped - fresh client for each test.
    """
    return app.test_client()


@pytest.fixture(scope='function')
def db_session(app):
    """
    Provide database session for tests.

    Each test gets a clean database state.
    """
    with app.app_context():
        yield db.session
        db.session.rollback()


@pytest.fixture
def sample_customer(app):
    """
    Create a sample customer for testing.
    """
    with app.app_context():
        customer = Customer(
            name='John Doe',
            email='john.doe@example.com',
            phone='+1234567890',
            company='Acme Inc',
            notes='A valuable customer with great potential.',
            status='active'
        )
        db.session.add(customer)
        db.session.commit()
        # Refresh to get the ID
        db.session.refresh(customer)
        yield customer


@pytest.fixture
def sample_customers(app):
    """
    Create multiple sample customers for testing.
    """
    with app.app_context():
        customers = [
            Customer(
                name='John Doe',
                email='john@example.com',
                company='Acme Inc',
                status='active',
                notes='Great customer!'
            ),
            Customer(
                name='Jane Smith',
                email='jane@example.com',
                company='Tech Corp',
                status='active',
                notes='Needs follow-up.'
            ),
            Customer(
                name='Bob Wilson',
                email='bob@example.com',
                company='Acme Inc',
                status='lead',
                notes='New lead from conference.'
            ),
            Customer(
                name='Alice Brown',
                email='alice@example.com',
                company='StartupXYZ',
                status='inactive',
                notes='Account closed.'
            ),
        ]

        for customer in customers:
            db.session.add(customer)

        db.session.commit()

        # Refresh all to get IDs
        for customer in customers:
            db.session.refresh(customer)

        yield customers


@pytest.fixture
def auth_headers():
    """
    Return headers with API key for authenticated requests.
    """
    return {'X-API-Key': 'test-api-key'}


@pytest.fixture
def json_headers():
    """
    Return headers for JSON requests.
    """
    return {'Content-Type': 'application/json'}


# |su:66) FACTORY PATTERN - Creates test data with defaults. Override specific fields as needed.
class CustomerFactory:
    """Factory for creating customer test data."""

    _counter = 0

    @classmethod
    def reset(cls):
        """Reset the counter."""
        cls._counter = 0

    @classmethod
    def create(cls, **kwargs):
        """Create a customer with optional overrides."""
        cls._counter += 1
        # |su:67) DEFAULT VALUES - Provide sensible defaults, allow overrides via **kwargs
        defaults = {
            'name': f'Test Customer {cls._counter}',
            'email': f'test{cls._counter}@example.com',
            'status': 'lead'
        }
        defaults.update(kwargs)

        customer = Customer(**defaults)
        db.session.add(customer)
        db.session.commit()
        db.session.refresh(customer)
        return customer

    @classmethod
    def create_batch(cls, count, **kwargs):
        """Create multiple customers."""
        customers = []
        for i in range(count):
            # Make email unique for each customer in batch
            customer_kwargs = kwargs.copy()
            if 'email' not in customer_kwargs:
                cls._counter += 1
                customer_kwargs['email'] = f'batch{cls._counter}@example.com'
            customers.append(cls.create(**customer_kwargs))
        return customers


@pytest.fixture
def customer_factory(app):
    """Provide customer factory bound to test session."""
    with app.app_context():
        CustomerFactory.reset()
        yield CustomerFactory


# Reset factory counter before each test module
@pytest.fixture(autouse=True)
def reset_factory():
    """Reset factory counters before each test."""
    CustomerFactory.reset()
