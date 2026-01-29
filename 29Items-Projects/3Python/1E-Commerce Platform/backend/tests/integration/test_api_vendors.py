"""Integration tests for vendor API."""
import pytest
from decimal import Decimal
from rest_framework import status


@pytest.mark.django_db
class TestVendorAPI:
    """Tests for vendor endpoints."""

    def test_vendor_registration(self, authenticated_client):
        """Test vendor registration."""
        response = authenticated_client.post('/api/v1/vendors/register/', {
            'business_name': 'My Test Business',
            'business_email': 'business@test.com',
            'description': 'A test business description'
        })

        assert response.status_code == status.HTTP_201_CREATED
        assert response.data['business_name'] == 'My Test Business'
        assert response.data['status'] == 'pending'

    def test_vendor_registration_duplicate(self, vendor_client):
        """Test vendor cannot register twice."""
        response = vendor_client.post('/api/v1/vendors/register/', {
            'business_name': 'Another Business',
            'business_email': 'another@test.com'
        })

        assert response.status_code == status.HTTP_400_BAD_REQUEST

    def test_get_vendor_profile(self, vendor_client):
        """Test getting vendor profile."""
        response = vendor_client.get('/api/v1/vendors/profile/')

        assert response.status_code == status.HTTP_200_OK
        assert 'business_name' in response.data

    def test_update_vendor_profile(self, vendor_client):
        """Test updating vendor profile."""
        response = vendor_client.patch('/api/v1/vendors/profile/', {
            'description': 'Updated description'
        })

        assert response.status_code == status.HTTP_200_OK
        assert response.data['description'] == 'Updated description'

    def test_list_vendor_products(self, vendor_client, product_factory):
        """Test listing vendor's products."""
        user = vendor_client.user
        vendor = user.vendor

        product_factory(vendor=vendor)
        product_factory(vendor=vendor)

        response = vendor_client.get('/api/v1/vendors/products/')

        assert response.status_code == status.HTTP_200_OK
        data = response.data.get('results', response.data)
        assert len(data) >= 2

    def test_create_product(self, vendor_client, category_factory):
        """Test vendor creating a product."""
        category = category_factory()

        response = vendor_client.post('/api/v1/vendors/products/', {
            'name': 'New Product',
            'category': category.id,
            'price': '99.99',
            'description': 'Product description',
            'short_description': 'Short desc'
        })

        assert response.status_code == status.HTTP_201_CREATED
        assert response.data['name'] == 'New Product'

    def test_update_product(self, vendor_client, product_factory):
        """Test vendor updating own product."""
        vendor = vendor_client.user.vendor
        product = product_factory(vendor=vendor)

        response = vendor_client.patch(f'/api/v1/vendors/products/{product.id}/', {
            'name': 'Updated Product Name'
        })

        assert response.status_code == status.HTTP_200_OK
        assert response.data['name'] == 'Updated Product Name'

    def test_cannot_update_other_vendor_product(self, vendor_client, product_factory, vendor_factory):
        """Test vendor cannot update another vendor's product."""
        other_vendor = vendor_factory()
        other_product = product_factory(vendor=other_vendor)

        response = vendor_client.patch(f'/api/v1/vendors/products/{other_product.id}/', {
            'name': 'Hacked Name'
        })

        assert response.status_code == status.HTTP_404_NOT_FOUND

    def test_vendor_analytics(self, vendor_client, order_factory, product_factory):
        """Test getting vendor analytics."""
        vendor = vendor_client.user.vendor
        product = product_factory(vendor=vendor)

        response = vendor_client.get('/api/v1/vendors/analytics/')

        assert response.status_code == status.HTTP_200_OK
        assert 'total_revenue' in response.data or 'revenue' in response.data

    def test_vendor_orders(self, vendor_client, order_factory, product_factory, user_factory):
        """Test listing vendor's orders."""
        vendor = vendor_client.user.vendor
        product = product_factory(vendor=vendor)
        customer = user_factory()
        order_factory(user=customer, products=[{'product': product}])

        response = vendor_client.get('/api/v1/vendors/orders/')

        assert response.status_code == status.HTTP_200_OK


@pytest.mark.django_db
class TestAdminVendorAPI:
    """Tests for admin vendor management."""

    def test_list_pending_vendors(self, admin_client, vendor_factory):
        """Test admin listing pending vendors."""
        from apps.vendors.models import Vendor

        vendor_factory(status=Vendor.Status.PENDING)
        vendor_factory(status=Vendor.Status.PENDING)

        response = admin_client.get('/api/v1/vendors/admin/pending/')

        assert response.status_code == status.HTTP_200_OK

    def test_approve_vendor(self, admin_client, vendor_factory):
        """Test admin approving vendor."""
        from apps.vendors.models import Vendor

        vendor = vendor_factory(status=Vendor.Status.PENDING)

        response = admin_client.post(f'/api/v1/vendors/admin/{vendor.id}/approve/')

        assert response.status_code == status.HTTP_200_OK
        assert response.data['status'] == 'approved'

    def test_reject_vendor(self, admin_client, vendor_factory):
        """Test admin rejecting vendor."""
        from apps.vendors.models import Vendor

        vendor = vendor_factory(status=Vendor.Status.PENDING)

        response = admin_client.post(f'/api/v1/vendors/admin/{vendor.id}/reject/', {
            'reason': 'Incomplete documentation'
        })

        assert response.status_code == status.HTTP_200_OK
        assert response.data['status'] == 'rejected'

    def test_suspend_vendor(self, admin_client, vendor_factory, product_factory):
        """Test admin suspending vendor."""
        from apps.vendors.models import Vendor

        vendor = vendor_factory(status=Vendor.Status.APPROVED)
        product_factory(vendor=vendor)

        response = admin_client.post(f'/api/v1/vendors/admin/{vendor.id}/suspend/', {
            'reason': 'Policy violation'
        })

        assert response.status_code == status.HTTP_200_OK
        assert response.data['status'] == 'suspended'

    def test_non_admin_cannot_approve(self, vendor_client, vendor_factory):
        """Test non-admin cannot approve vendors."""
        from apps.vendors.models import Vendor

        vendor = vendor_factory(status=Vendor.Status.PENDING)

        response = vendor_client.post(f'/api/v1/vendors/admin/{vendor.id}/approve/')

        assert response.status_code == status.HTTP_403_FORBIDDEN


@pytest.mark.django_db
class TestPublicVendorAPI:
    """Tests for public vendor endpoints."""

    def test_list_vendors(self, api_client, vendor_factory):
        """Test listing public vendors."""
        from apps.vendors.models import Vendor

        vendor_factory(status=Vendor.Status.APPROVED)
        vendor_factory(status=Vendor.Status.APPROVED)

        response = api_client.get('/api/v1/vendors/')

        assert response.status_code == status.HTTP_200_OK

    def test_get_vendor_detail(self, api_client, vendor_factory):
        """Test getting vendor public profile."""
        from apps.vendors.models import Vendor

        vendor = vendor_factory(status=Vendor.Status.APPROVED)

        response = api_client.get(f'/api/v1/vendors/{vendor.slug}/')

        assert response.status_code == status.HTTP_200_OK
        assert response.data['business_name'] == vendor.business_name

    def test_vendor_products(self, api_client, vendor_factory, product_factory):
        """Test listing vendor's public products."""
        from apps.vendors.models import Vendor

        vendor = vendor_factory(status=Vendor.Status.APPROVED)
        product_factory(vendor=vendor)
        product_factory(vendor=vendor)

        response = api_client.get(f'/api/v1/vendors/{vendor.slug}/products/')

        assert response.status_code == status.HTTP_200_OK
