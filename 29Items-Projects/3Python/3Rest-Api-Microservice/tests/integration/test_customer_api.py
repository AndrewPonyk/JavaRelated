"""
Integration Tests for Customer API Endpoints
"""
import pytest
import json


class TestCustomerCRUD:
    """Integration tests for /api/customers CRUD operations."""

    def test_get_customers_empty(self, client):
        """Test getting customers when database is empty."""
        response = client.get('/api/customers/')

        assert response.status_code == 200
        data = response.get_json()
        assert 'data' in data
        assert 'pagination' in data
        assert data['pagination']['total'] == 0

    def test_get_customers_with_data(self, client, sample_customers):
        """Test getting customers with data."""
        response = client.get('/api/customers/')

        assert response.status_code == 200
        data = response.get_json()
        assert len(data['data']) == 4
        assert data['pagination']['total'] == 4

    def test_get_customers_pagination(self, client, sample_customers):
        """Test customer pagination."""
        response = client.get('/api/customers/?page=1&per_page=2')

        assert response.status_code == 200
        data = response.get_json()
        assert len(data['data']) == 2
        assert data['pagination']['page'] == 1
        assert data['pagination']['per_page'] == 2
        assert data['pagination']['pages'] == 2

    def test_get_customers_filter_by_status(self, client, sample_customers):
        """Test filtering customers by status."""
        response = client.get('/api/customers/?status=active')

        assert response.status_code == 200
        data = response.get_json()
        assert all(c['status'] == 'active' for c in data['data'])
        assert len(data['data']) == 2

    def test_get_customer_by_id(self, client, sample_customer):
        """Test getting a single customer by ID."""
        response = client.get(f'/api/customers/{sample_customer.id}')

        assert response.status_code == 200
        data = response.get_json()
        assert data['data']['id'] == sample_customer.id
        assert data['data']['name'] == sample_customer.name

    def test_get_customer_not_found(self, client):
        """Test getting non-existent customer."""
        response = client.get('/api/customers/99999')

        assert response.status_code == 404
        data = response.get_json()
        assert 'error' in data

    def test_create_customer_success(self, client, json_headers):
        """Test creating a new customer."""
        customer_data = {
            'name': 'New Customer',
            'email': 'new@example.com',
            'company': 'New Corp'
        }

        response = client.post(
            '/api/customers/',
            data=json.dumps(customer_data),
            headers=json_headers
        )

        assert response.status_code == 201
        data = response.get_json()
        assert data['data']['name'] == 'New Customer'
        assert data['data']['email'] == 'new@example.com'
        assert data['data']['id'] is not None
        assert data['data']['status'] == 'lead'

    def test_create_customer_with_all_fields(self, client, json_headers):
        """Test creating customer with all fields."""
        customer_data = {
            'name': 'Full Customer',
            'email': 'full@example.com',
            'phone': '+1234567890',
            'company': 'Full Corp',
            'notes': 'Some notes',
            'status': 'active'
        }

        response = client.post(
            '/api/customers/',
            data=json.dumps(customer_data),
            headers=json_headers
        )

        assert response.status_code == 201
        data = response.get_json()
        assert data['data']['phone'] == '+1234567890'
        assert data['data']['status'] == 'active'

    def test_create_customer_missing_name(self, client, json_headers):
        """Test creating customer without required name."""
        customer_data = {
            'email': 'test@example.com'
        }

        response = client.post(
            '/api/customers/',
            data=json.dumps(customer_data),
            headers=json_headers
        )

        assert response.status_code == 400

    def test_create_customer_invalid_email(self, client, json_headers):
        """Test creating customer with invalid email."""
        customer_data = {
            'name': 'Test User',
            'email': 'invalid-email'
        }

        response = client.post(
            '/api/customers/',
            data=json.dumps(customer_data),
            headers=json_headers
        )

        assert response.status_code == 400

    def test_create_customer_duplicate_email(self, client, json_headers, sample_customer):
        """Test creating customer with duplicate email."""
        customer_data = {
            'name': 'Another Customer',
            'email': sample_customer.email
        }

        response = client.post(
            '/api/customers/',
            data=json.dumps(customer_data),
            headers=json_headers
        )

        assert response.status_code == 400

    def test_update_customer_success(self, client, json_headers, sample_customer):
        """Test updating a customer."""
        update_data = {
            'name': 'Updated Name',
            'status': 'inactive'
        }

        response = client.put(
            f'/api/customers/{sample_customer.id}',
            data=json.dumps(update_data),
            headers=json_headers
        )

        assert response.status_code == 200
        data = response.get_json()
        assert data['data']['name'] == 'Updated Name'
        assert data['data']['status'] == 'inactive'

    def test_update_customer_not_found(self, client, json_headers):
        """Test updating non-existent customer."""
        response = client.put(
            '/api/customers/99999',
            data=json.dumps({'name': 'Test'}),
            headers=json_headers
        )

        assert response.status_code == 404

    def test_delete_customer_success(self, client, sample_customer):
        """Test deleting a customer."""
        customer_id = sample_customer.id
        response = client.delete(f'/api/customers/{customer_id}')

        assert response.status_code == 204

        # Verify deletion
        get_response = client.get(f'/api/customers/{customer_id}')
        assert get_response.status_code == 404

    def test_delete_customer_not_found(self, client):
        """Test deleting non-existent customer."""
        response = client.delete('/api/customers/99999')

        assert response.status_code == 404


class TestCustomerAPIValidation:
    """Tests for API input validation."""

    @pytest.mark.parametrize("email", [
        'test@example.com',
        'test+tag@example.com',
        'test.name@example.co.uk',
    ])
    def test_valid_email_formats(self, client, json_headers, email):
        """Test various valid email formats."""
        customer_data = {
            'name': 'Test User',
            'email': email
        }

        response = client.post(
            '/api/customers/',
            data=json.dumps(customer_data),
            headers=json_headers
        )

        assert response.status_code == 201

    @pytest.mark.parametrize("status", ['active', 'inactive', 'lead'])
    def test_valid_status_values(self, client, json_headers, status):
        """Test valid status values."""
        customer_data = {
            'name': f'Test User {status}',
            'email': f'{status}@example.com',
            'status': status
        }

        response = client.post(
            '/api/customers/',
            data=json.dumps(customer_data),
            headers=json_headers
        )

        assert response.status_code == 201
        data = response.get_json()
        assert data['data']['status'] == status


class TestAPIEndpoints:
    """Tests for general API endpoints."""

    def test_api_welcome(self, client):
        """Test API welcome endpoint."""
        response = client.get('/api/')

        assert response.status_code == 200
        data = response.get_json()
        assert 'message' in data
        assert 'version' in data

    def test_api_health(self, client):
        """Test health check endpoint."""
        response = client.get('/api/health')

        assert response.status_code == 200
        data = response.get_json()
        assert data['status'] == 'healthy'

    def test_dashboard_page(self, client):
        """Test dashboard page renders."""
        response = client.get('/')

        assert response.status_code == 200
        assert b'Customer' in response.data or b'Dashboard' in response.data
