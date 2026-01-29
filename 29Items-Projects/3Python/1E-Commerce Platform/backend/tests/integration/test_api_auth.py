"""Integration tests for authentication API."""
import pytest
from rest_framework import status


@pytest.mark.django_db
class TestAuthAPI:
    """Tests for authentication endpoints."""

    def test_user_registration(self, api_client):
        """Test user registration endpoint."""
        response = api_client.post('/api/v1/auth/register/', {
            'email': 'newuser@example.com',
            'password': 'securepass123',
            'password_confirm': 'securepass123',
            'first_name': 'New',
            'last_name': 'User'
        })

        assert response.status_code == status.HTTP_201_CREATED
        assert 'access' in response.data
        assert 'refresh' in response.data
        assert response.data['user']['email'] == 'newuser@example.com'

    def test_registration_password_mismatch(self, api_client):
        """Test registration with mismatched passwords."""
        response = api_client.post('/api/v1/auth/register/', {
            'email': 'newuser@example.com',
            'password': 'securepass123',
            'password_confirm': 'differentpass',
            'first_name': 'New',
            'last_name': 'User'
        })

        assert response.status_code == status.HTTP_400_BAD_REQUEST

    def test_registration_duplicate_email(self, api_client, user_factory):
        """Test registration with existing email."""
        existing_user = user_factory(email='existing@example.com')

        response = api_client.post('/api/v1/auth/register/', {
            'email': 'existing@example.com',
            'password': 'securepass123',
            'password_confirm': 'securepass123',
            'first_name': 'New',
            'last_name': 'User'
        })

        assert response.status_code == status.HTTP_400_BAD_REQUEST

    def test_user_login(self, api_client, user_factory):
        """Test user login endpoint."""
        user = user_factory(email='login@example.com', password='testpass123')

        response = api_client.post('/api/v1/auth/login/', {
            'email': 'login@example.com',
            'password': 'testpass123'
        })

        assert response.status_code == status.HTTP_200_OK
        assert 'access' in response.data
        assert 'refresh' in response.data

    def test_login_invalid_credentials(self, api_client, user_factory):
        """Test login with invalid credentials."""
        user_factory(email='login@example.com', password='testpass123')

        response = api_client.post('/api/v1/auth/login/', {
            'email': 'login@example.com',
            'password': 'wrongpassword'
        })

        assert response.status_code == status.HTTP_401_UNAUTHORIZED

    def test_token_refresh(self, api_client, user_factory):
        """Test token refresh endpoint."""
        user = user_factory()

        # First login to get tokens
        login_response = api_client.post('/api/v1/auth/login/', {
            'email': user.email,
            'password': 'testpass123'
        })

        refresh_token = login_response.data['refresh']

        # Refresh the token
        response = api_client.post('/api/v1/auth/token/refresh/', {
            'refresh': refresh_token
        })

        assert response.status_code == status.HTTP_200_OK
        assert 'access' in response.data

    def test_get_profile(self, authenticated_client):
        """Test getting user profile."""
        response = authenticated_client.get('/api/v1/auth/profile/')

        assert response.status_code == status.HTTP_200_OK
        assert 'email' in response.data
        assert 'first_name' in response.data

    def test_update_profile(self, authenticated_client):
        """Test updating user profile."""
        response = authenticated_client.patch('/api/v1/auth/profile/', {
            'first_name': 'Updated',
            'last_name': 'Name'
        })

        assert response.status_code == status.HTTP_200_OK
        assert response.data['first_name'] == 'Updated'
        assert response.data['last_name'] == 'Name'

    def test_change_password(self, authenticated_client):
        """Test password change endpoint."""
        response = authenticated_client.put('/api/v1/auth/password/change/', {
            'old_password': 'testpass123',
            'new_password': 'newsecurepass456'
        })

        assert response.status_code == status.HTTP_200_OK

    def test_change_password_wrong_old_password(self, authenticated_client):
        """Test password change with wrong old password."""
        response = authenticated_client.put('/api/v1/auth/password/change/', {
            'old_password': 'wrongoldpassword',
            'new_password': 'newsecurepass456'
        })

        assert response.status_code == status.HTTP_400_BAD_REQUEST

    def test_logout(self, authenticated_client, user_factory):
        """Test logout endpoint."""
        user = user_factory()

        # Get a refresh token
        login_response = authenticated_client.post('/api/v1/auth/login/', {
            'email': user.email,
            'password': 'testpass123'
        })

        refresh_token = login_response.data['refresh']

        # Logout
        response = authenticated_client.post('/api/v1/auth/logout/', {
            'refresh': refresh_token
        })

        assert response.status_code in [status.HTTP_200_OK, status.HTTP_204_NO_CONTENT]


@pytest.mark.django_db
class TestAddressAPI:
    """Tests for address management endpoints."""

    def test_create_address(self, authenticated_client):
        """Test creating a new address."""
        response = authenticated_client.post('/api/v1/auth/addresses/', {
            'address_type': 'shipping',
            'street_address': '123 Test Street',
            'city': 'Test City',
            'state': 'CA',
            'postal_code': '12345',
            'country': 'US'
        })

        assert response.status_code == status.HTTP_201_CREATED
        assert response.data['street_address'] == '123 Test Street'

    def test_list_addresses(self, authenticated_client, address_factory):
        """Test listing user addresses."""
        user = authenticated_client.user
        address_factory(user=user)
        address_factory(user=user, address_type='billing')

        response = authenticated_client.get('/api/v1/auth/addresses/')

        assert response.status_code == status.HTTP_200_OK
        assert len(response.data) >= 2

    def test_update_address(self, authenticated_client, address_factory):
        """Test updating an address."""
        user = authenticated_client.user
        address = address_factory(user=user)

        response = authenticated_client.patch(f'/api/v1/auth/addresses/{address.id}/', {
            'city': 'New City'
        })

        assert response.status_code == status.HTTP_200_OK
        assert response.data['city'] == 'New City'

    def test_delete_address(self, authenticated_client, address_factory):
        """Test deleting an address."""
        user = authenticated_client.user
        address = address_factory(user=user)

        response = authenticated_client.delete(f'/api/v1/auth/addresses/{address.id}/')

        assert response.status_code == status.HTTP_204_NO_CONTENT

    def test_set_default_address(self, authenticated_client, address_factory):
        """Test setting default address."""
        user = authenticated_client.user
        address1 = address_factory(user=user, is_default=True)
        address2 = address_factory(user=user, is_default=False)

        response = authenticated_client.patch(f'/api/v1/auth/addresses/{address2.id}/', {
            'is_default': True
        })

        assert response.status_code == status.HTTP_200_OK
        assert response.data['is_default'] is True
