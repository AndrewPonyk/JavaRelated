"""Integration tests for cart API."""
import pytest
from decimal import Decimal

from rest_framework import status


class TestCartAPI:
    """Tests for Cart API endpoints."""

    def test_get_empty_cart(self, authenticated_client):
        """Test getting empty cart."""
        response = authenticated_client.get('/api/v1/cart/')

        assert response.status_code == status.HTTP_200_OK
        assert response.data['is_empty'] is True
        assert response.data['item_count'] == 0

    def test_add_item_to_cart(self, authenticated_client, product_factory):
        """Test adding item to cart."""
        product = product_factory(price=Decimal('50.00'))

        response = authenticated_client.post('/api/v1/cart/add/', {
            'product_id': str(product.id),
            'quantity': 2
        })

        assert response.status_code == status.HTTP_201_CREATED
        assert response.data['item_count'] == 2
        assert Decimal(response.data['subtotal']) == Decimal('100.00')

    def test_update_cart_item_quantity(self, authenticated_client, product_factory):
        """Test updating cart item quantity."""
        product = product_factory()

        # Add item first
        authenticated_client.post('/api/v1/cart/add/', {
            'product_id': str(product.id),
            'quantity': 1
        })

        # Get cart to find item ID
        cart_response = authenticated_client.get('/api/v1/cart/')
        item_id = cart_response.data['items'][0]['id']

        # Update quantity
        response = authenticated_client.patch(f'/api/v1/cart/items/{item_id}/', {
            'quantity': 5
        })

        assert response.status_code == status.HTTP_200_OK
        assert response.data['item_count'] == 5

    def test_remove_item_from_cart(self, authenticated_client, product_factory):
        """Test removing item from cart."""
        product = product_factory()

        # Add item
        authenticated_client.post('/api/v1/cart/add/', {
            'product_id': str(product.id),
            'quantity': 1
        })

        # Get item ID
        cart_response = authenticated_client.get('/api/v1/cart/')
        item_id = cart_response.data['items'][0]['id']

        # Remove item
        response = authenticated_client.delete(f'/api/v1/cart/items/{item_id}/remove/')

        assert response.status_code == status.HTTP_200_OK
        assert response.data['is_empty'] is True

    def test_clear_cart(self, authenticated_client, product_factory):
        """Test clearing entire cart."""
        product = product_factory()

        # Add items
        authenticated_client.post('/api/v1/cart/add/', {
            'product_id': str(product.id),
            'quantity': 3
        })

        # Clear cart
        response = authenticated_client.post('/api/v1/cart/clear/')

        assert response.status_code == status.HTTP_200_OK
        assert response.data['is_empty'] is True

    def test_guest_cart(self, api_client, product_factory):
        """Test guest cart functionality."""
        product = product_factory()

        # Guest should be able to add items
        response = api_client.post('/api/v1/cart/add/', {
            'product_id': str(product.id),
            'quantity': 1
        })

        assert response.status_code == status.HTTP_201_CREATED
