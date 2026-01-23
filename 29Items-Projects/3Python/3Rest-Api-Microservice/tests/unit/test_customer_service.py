"""
Unit Tests for Customer Service
"""
import pytest
from app.services.customer_service import CustomerService
from app.utils.exceptions import ValidationError
from app.extensions import db
from app.models.customer import Customer


class TestCustomerServiceCreate:
    """Test cases for CustomerService.create()."""

    def test_create_customer_success(self, app):
        """Test successful customer creation."""
        with app.app_context():
            service = CustomerService()
            data = {
                'name': 'Test User',
                'email': 'test@example.com',
                'company': 'Test Co'
            }

            customer = service.create(data)

            assert customer.id is not None
            assert customer.name == 'Test User'
            assert customer.email == 'test@example.com'
            assert customer.status == 'lead'

    def test_create_customer_with_all_fields(self, app):
        """Test creating customer with all optional fields."""
        with app.app_context():
            service = CustomerService()
            data = {
                'name': 'Full Customer',
                'email': 'full@example.com',
                'phone': '+1234567890',
                'company': 'Full Corp',
                'notes': 'Some notes here',
                'status': 'active'
            }

            customer = service.create(data)

            assert customer.name == 'Full Customer'
            assert customer.phone == '+1234567890'
            assert customer.company == 'Full Corp'
            assert customer.notes == 'Some notes here'
            assert customer.status == 'active'

    def test_create_customer_duplicate_email(self, app, sample_customer):
        """Test that duplicate email raises validation error."""
        with app.app_context():
            service = CustomerService()
            data = {
                'name': 'Another User',
                'email': 'john.doe@example.com'  # Same as sample_customer
            }

            with pytest.raises(ValidationError) as exc_info:
                service.create(data)

            assert 'Email already exists' in str(exc_info.value.message)


class TestCustomerServiceRead:
    """Test cases for CustomerService read operations."""

    def test_get_by_id_existing(self, app, sample_customer):
        """Test getting existing customer by ID."""
        with app.app_context():
            service = CustomerService()
            customer = service.get_by_id(sample_customer.id)

            assert customer is not None
            assert customer.id == sample_customer.id
            assert customer.name == 'John Doe'

    def test_get_by_id_not_found(self, app):
        """Test getting non-existent customer returns None."""
        with app.app_context():
            service = CustomerService()
            customer = service.get_by_id(99999)

            assert customer is None

    def test_get_all_empty(self, app):
        """Test getting customers when none exist."""
        with app.app_context():
            service = CustomerService()
            result = service.get_all()

            assert result['total'] == 0
            assert len(result['items']) == 0

    def test_get_all_with_data(self, app, sample_customers):
        """Test getting all customers."""
        with app.app_context():
            service = CustomerService()
            result = service.get_all()

            assert result['total'] == 4
            assert len(result['items']) == 4


class TestCustomerServiceUpdate:
    """Test cases for CustomerService.update()."""

    def test_update_customer_success(self, app, sample_customer):
        """Test successful customer update."""
        with app.app_context():
            service = CustomerService()
            data = {'name': 'Updated Name', 'status': 'inactive'}

            customer = service.update(sample_customer.id, data)

            assert customer.name == 'Updated Name'
            assert customer.status == 'inactive'
            # Email should remain unchanged
            assert customer.email == 'john.doe@example.com'

    def test_update_customer_email(self, app, sample_customer):
        """Test updating customer email."""
        with app.app_context():
            service = CustomerService()
            data = {'email': 'newemail@example.com'}

            customer = service.update(sample_customer.id, data)

            assert customer.email == 'newemail@example.com'

    def test_update_customer_not_found(self, app):
        """Test updating non-existent customer returns None."""
        with app.app_context():
            service = CustomerService()
            customer = service.update(99999, {'name': 'New Name'})

            assert customer is None

    def test_update_customer_duplicate_email(self, app, sample_customers):
        """Test updating to duplicate email raises error."""
        with app.app_context():
            service = CustomerService()
            # Try to update first customer's email to second customer's email
            customer1 = sample_customers[0]
            customer2 = sample_customers[1]

            with pytest.raises(ValidationError) as exc_info:
                service.update(customer1.id, {'email': customer2.email})

            assert 'Email already exists' in str(exc_info.value.message)


class TestCustomerServiceDelete:
    """Test cases for CustomerService.delete()."""

    def test_delete_customer_success(self, app, sample_customer):
        """Test successful customer deletion."""
        with app.app_context():
            service = CustomerService()
            customer_id = sample_customer.id

            result = service.delete(customer_id)

            assert result is True
            assert service.get_by_id(customer_id) is None

    def test_delete_customer_not_found(self, app):
        """Test deleting non-existent customer returns False."""
        with app.app_context():
            service = CustomerService()
            result = service.delete(99999)

            assert result is False


class TestCustomerServiceFiltering:
    """Test cases for CustomerService filtering and pagination."""

    @pytest.mark.parametrize("status,expected_count", [
        ('active', 2),
        ('inactive', 1),
        ('lead', 1),
        (None, 4),
    ])
    def test_get_all_with_status_filter(self, app, sample_customers, status, expected_count):
        """Test filtering customers by status."""
        with app.app_context():
            service = CustomerService()
            result = service.get_all(status=status)

            assert result['total'] == expected_count

    @pytest.mark.parametrize("page,per_page,expected_items", [
        (1, 2, 2),
        (2, 2, 2),
        (3, 2, 0),
        (1, 10, 4),
    ])
    def test_get_all_pagination(self, app, sample_customers, page, per_page, expected_items):
        """Test pagination of customer list."""
        with app.app_context():
            service = CustomerService()
            result = service.get_all(page=page, per_page=per_page)

            assert len(result['items']) == expected_items
            assert result['page'] == page
            assert result['per_page'] == per_page


class TestCustomerServiceSentiment:
    """Test cases for CustomerService sentiment operations."""

    def test_update_sentiment(self, app, sample_customer):
        """Test updating customer sentiment score."""
        with app.app_context():
            service = CustomerService()

            customer = service.update_sentiment(sample_customer.id, 0.85)

            assert customer is not None
            assert customer.sentiment_score == 0.85

    def test_update_sentiment_not_found(self, app):
        """Test updating sentiment for non-existent customer."""
        with app.app_context():
            service = CustomerService()
            customer = service.update_sentiment(99999, 0.5)

            assert customer is None
