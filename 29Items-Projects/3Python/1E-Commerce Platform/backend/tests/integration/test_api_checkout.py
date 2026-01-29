"""Integration tests for checkout API."""
import pytest
from decimal import Decimal
from rest_framework import status


@pytest.mark.django_db
class TestCheckoutAPI:
    """Tests for checkout endpoints."""

    def test_initiate_checkout(self, authenticated_client, product_factory, mock_stripe):
        """Test initiating checkout process."""
        product = product_factory(price=Decimal('50.00'))

        # Add item to cart first
        authenticated_client.post('/api/v1/cart/add/', {
            'product_id': str(product.id),
            'quantity': 2
        })

        response = authenticated_client.post('/api/v1/checkout/initiate/', {
            'shipping_address': {
                'street_address': '123 Test St',
                'city': 'Test City',
                'state': 'CA',
                'postal_code': '12345',
                'country': 'US'
            }
        })

        assert response.status_code == status.HTTP_200_OK
        assert 'client_secret' in response.data
        assert 'checkout_session_id' in response.data

    def test_initiate_checkout_empty_cart(self, authenticated_client, mock_stripe):
        """Test checkout with empty cart."""
        response = authenticated_client.post('/api/v1/checkout/initiate/', {
            'shipping_address': {
                'street_address': '123 Test St',
                'city': 'Test City',
                'state': 'CA',
                'postal_code': '12345',
                'country': 'US'
            }
        })

        assert response.status_code == status.HTTP_400_BAD_REQUEST

    def test_confirm_payment(self, authenticated_client, product_factory, mock_stripe, mocker):
        """Test confirming payment."""
        product = product_factory(price=Decimal('50.00'))

        # Add item to cart
        authenticated_client.post('/api/v1/cart/add/', {
            'product_id': str(product.id),
            'quantity': 1
        })

        # Initiate checkout
        checkout_response = authenticated_client.post('/api/v1/checkout/initiate/', {
            'shipping_address': {
                'street_address': '123 Test St',
                'city': 'Test City',
                'state': 'CA',
                'postal_code': '12345',
                'country': 'US'
            }
        })

        checkout_session_id = checkout_response.data['checkout_session_id']

        # Confirm payment
        response = authenticated_client.post('/api/v1/checkout/confirm/', {
            'checkout_session_id': checkout_session_id,
            'payment_intent_id': 'pi_test_123'
        })

        assert response.status_code == status.HTTP_200_OK
        assert 'order' in response.data
        assert response.data['order']['status'] == 'confirmed'

    def test_checkout_summary(self, authenticated_client, product_factory):
        """Test getting checkout summary."""
        product = product_factory(price=Decimal('100.00'))

        # Add item to cart
        authenticated_client.post('/api/v1/cart/add/', {
            'product_id': str(product.id),
            'quantity': 2
        })

        response = authenticated_client.get('/api/v1/checkout/summary/')

        assert response.status_code == status.HTTP_200_OK
        assert 'subtotal' in response.data
        assert 'tax_amount' in response.data
        assert 'shipping_amount' in response.data
        assert 'total' in response.data


@pytest.mark.django_db
class TestCouponAPI:
    """Tests for coupon endpoints."""

    def test_apply_valid_coupon(self, authenticated_client, coupon_factory, product_factory):
        """Test applying a valid coupon."""
        from apps.checkout.models import Coupon

        coupon = coupon_factory(
            code='SAVE20',
            discount_type=Coupon.DiscountType.PERCENTAGE,
            discount_value=Decimal('20.00')
        )
        product = product_factory(price=Decimal('100.00'))

        # Add item to cart
        authenticated_client.post('/api/v1/cart/add/', {
            'product_id': str(product.id),
            'quantity': 1
        })

        response = authenticated_client.post('/api/v1/checkout/apply-coupon/', {
            'code': 'SAVE20'
        })

        assert response.status_code == status.HTTP_200_OK
        assert 'discount_amount' in response.data
        assert Decimal(response.data['discount_amount']) == Decimal('20.00')

    def test_apply_invalid_coupon(self, authenticated_client, product_factory):
        """Test applying invalid coupon code."""
        product = product_factory()

        # Add item to cart
        authenticated_client.post('/api/v1/cart/add/', {
            'product_id': str(product.id),
            'quantity': 1
        })

        response = authenticated_client.post('/api/v1/checkout/apply-coupon/', {
            'code': 'INVALIDCODE'
        })

        assert response.status_code == status.HTTP_400_BAD_REQUEST

    def test_apply_expired_coupon(self, authenticated_client, coupon_factory, product_factory):
        """Test applying expired coupon."""
        from datetime import timedelta
        from django.utils import timezone

        coupon = coupon_factory(
            code='EXPIRED',
            valid_from=timezone.now() - timedelta(days=30),
            valid_until=timezone.now() - timedelta(days=1)
        )
        product = product_factory()

        # Add item to cart
        authenticated_client.post('/api/v1/cart/add/', {
            'product_id': str(product.id),
            'quantity': 1
        })

        response = authenticated_client.post('/api/v1/checkout/apply-coupon/', {
            'code': 'EXPIRED'
        })

        assert response.status_code == status.HTTP_400_BAD_REQUEST

    def test_apply_minimum_purchase_coupon(self, authenticated_client, coupon_factory, product_factory):
        """Test coupon with minimum purchase requirement."""
        from apps.checkout.models import Coupon

        coupon = coupon_factory(
            code='MIN100',
            discount_type=Coupon.DiscountType.FIXED,
            discount_value=Decimal('10.00'),
            minimum_purchase_amount=Decimal('100.00')
        )
        product = product_factory(price=Decimal('50.00'))

        # Add item to cart (total $50, below minimum)
        authenticated_client.post('/api/v1/cart/add/', {
            'product_id': str(product.id),
            'quantity': 1
        })

        response = authenticated_client.post('/api/v1/checkout/apply-coupon/', {
            'code': 'MIN100'
        })

        assert response.status_code == status.HTTP_400_BAD_REQUEST

    def test_remove_coupon(self, authenticated_client, coupon_factory, product_factory):
        """Test removing applied coupon."""
        coupon = coupon_factory(code='REMOVE')
        product = product_factory()

        # Add item to cart
        authenticated_client.post('/api/v1/cart/add/', {
            'product_id': str(product.id),
            'quantity': 1
        })

        # Apply coupon
        authenticated_client.post('/api/v1/checkout/apply-coupon/', {
            'code': 'REMOVE'
        })

        # Remove coupon
        response = authenticated_client.post('/api/v1/checkout/remove-coupon/')

        assert response.status_code == status.HTTP_200_OK


@pytest.mark.django_db
class TestOrderAPI:
    """Tests for order endpoints."""

    def test_list_orders(self, authenticated_client, order_factory, product_factory):
        """Test listing user orders."""
        user = authenticated_client.user
        product = product_factory()
        order_factory(user=user, products=[{'product': product}])
        order_factory(user=user, products=[{'product': product}])

        response = authenticated_client.get('/api/v1/checkout/orders/')

        assert response.status_code == status.HTTP_200_OK
        # Could be paginated
        data = response.data.get('results', response.data)
        assert len(data) >= 2

    def test_get_order_detail(self, authenticated_client, order_factory, product_factory):
        """Test getting order details."""
        user = authenticated_client.user
        product = product_factory()
        order = order_factory(user=user, products=[{'product': product}])

        response = authenticated_client.get(f'/api/v1/checkout/orders/{order.id}/')

        assert response.status_code == status.HTTP_200_OK
        assert response.data['id'] == order.id

    def test_cannot_view_other_user_order(self, authenticated_client, order_factory, user_factory, product_factory):
        """Test user cannot view another user's order."""
        other_user = user_factory()
        product = product_factory()
        other_order = order_factory(user=other_user, products=[{'product': product}])

        response = authenticated_client.get(f'/api/v1/checkout/orders/{other_order.id}/')

        assert response.status_code == status.HTTP_404_NOT_FOUND

    def test_cancel_pending_order(self, authenticated_client, order_factory, product_factory, mock_stripe):
        """Test cancelling a pending order."""
        from apps.checkout.models import Order

        user = authenticated_client.user
        product = product_factory()
        order = order_factory(
            user=user,
            products=[{'product': product}],
            status=Order.Status.PENDING
        )

        response = authenticated_client.post(f'/api/v1/checkout/orders/{order.id}/cancel/')

        assert response.status_code == status.HTTP_200_OK
        assert response.data['status'] == 'cancelled'

    def test_cannot_cancel_shipped_order(self, authenticated_client, order_factory, product_factory):
        """Test cannot cancel shipped order."""
        from apps.checkout.models import Order

        user = authenticated_client.user
        product = product_factory()
        order = order_factory(
            user=user,
            products=[{'product': product}],
            status=Order.Status.SHIPPED
        )

        response = authenticated_client.post(f'/api/v1/checkout/orders/{order.id}/cancel/')

        assert response.status_code == status.HTTP_400_BAD_REQUEST

    def test_filter_orders_by_status(self, authenticated_client, order_factory, product_factory):
        """Test filtering orders by status."""
        from apps.checkout.models import Order

        user = authenticated_client.user
        product = product_factory()
        order_factory(user=user, products=[{'product': product}], status=Order.Status.PENDING)
        order_factory(user=user, products=[{'product': product}], status=Order.Status.DELIVERED)

        response = authenticated_client.get('/api/v1/checkout/orders/', {'status': 'delivered'})

        assert response.status_code == status.HTTP_200_OK
        data = response.data.get('results', response.data)
        assert all(o['status'] == 'delivered' for o in data)
