"""Integration tests for product API."""
import pytest
from decimal import Decimal

from django.urls import reverse
from rest_framework import status


class TestProductAPI:
    """Tests for Product API endpoints."""

    def test_list_products(self, api_client, product_factory):
        """Test listing products."""
        product_factory(name='Product 1')
        product_factory(name='Product 2')

        response = api_client.get('/api/v1/products/')

        assert response.status_code == status.HTTP_200_OK
        # Response includes pagination
        assert len(response.data['results']) >= 2

    def test_get_product_detail(self, api_client, product_factory):
        """Test getting product details."""
        product = product_factory(name='Test Product')

        response = api_client.get(f'/api/v1/products/{product.slug}/')

        assert response.status_code == status.HTTP_200_OK
        assert response.data['name'] == 'Test Product'

    def test_product_not_found(self, api_client):
        """Test 404 for non-existent product."""
        response = api_client.get('/api/v1/products/non-existent-slug/')

        assert response.status_code == status.HTTP_404_NOT_FOUND

    def test_featured_products(self, api_client, product_factory):
        """Test featured products endpoint."""
        product_factory(name='Featured', is_featured=True)
        product_factory(name='Regular', is_featured=False)

        response = api_client.get('/api/v1/products/featured/')

        assert response.status_code == status.HTTP_200_OK
        assert all(p['is_featured'] for p in response.data)


class TestCategoryAPI:
    """Tests for Category API endpoints."""

    def test_list_categories(self, api_client, category_factory):
        """Test listing categories."""
        category_factory(name='Electronics')
        category_factory(name='Clothing')

        response = api_client.get('/api/v1/products/categories/')

        assert response.status_code == status.HTTP_200_OK
        assert len(response.data) >= 2

    def test_category_hierarchy(self, api_client, category_factory):
        """Test category with children."""
        parent = category_factory(name='Electronics')
        category_factory(name='Phones', parent=parent)

        response = api_client.get(f'/api/v1/products/categories/{parent.slug}/')

        assert response.status_code == status.HTTP_200_OK
        assert len(response.data['children']) == 1
