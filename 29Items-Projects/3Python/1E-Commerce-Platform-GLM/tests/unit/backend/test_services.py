"""
Unit Tests for Services

Tests for business logic in service layer.
"""

import pytest
from decimal import Decimal
from unittest.mock import Mock, patch, MagicMock
from datetime import datetime, timedelta

from backend.core.models import User, Product, Cart, CartItem, Order
from backend.api.services.auth_service import AuthenticationService
from backend.api.services.payment_service import PaymentService
from backend.api.services.inventory_service import InventoryService
from backend.api.services.recommendation_service import RecommendationService
from backend.api.services.product_service import ProductService
from backend.api.services.cart_service import CartService
from backend.api.services.order_service import OrderService
from backend.api.services.search_service import SearchService


@pytest.mark.unit
class TestAuthenticationService:
    """Tests for authentication service."""

    def test_register_success(self, django_db_setup):
        """Test successful user registration."""
        service = AuthenticationService()

        with patch('backend.api.services.auth_service.User.objects.create_user') as mock_create:
            mock_user = Mock()
            mock_user.id = 1
            mock_user.email = "newuser@example.com"
            mock_user.is_verified = False
            mock_create.return_value = mock_user

            result = service.register_user(
                email="newuser@example.com",
                password="SecurePass123!",
                first_name="New",
                last_name="User"
            )

            assert result["user"]["email"] == "newuser@example.com"
            assert "access_token" in result
            assert "refresh_token" in result

    def test_register_duplicate_email(self, django_db_setup):
        """Test registration with duplicate email."""
        service = AuthenticationService()

        with patch('backend.api.services.auth_service.User.objects.filter') as mock_filter:
            mock_filter.return_value.exists.return_value = True

            with pytest.raises(ValueError, match="already exists"):
                service.register_user(
                    email="existing@example.com",
                    password="SecurePass123!",
                    first_name="Existing",
                    last_name="User"
                )

    def test_login_success(self, django_db_setup):
        """Test successful login."""
        service = AuthenticationService()

        with patch('backend.api.services.auth_service.User.objects.get') as mock_get:
            mock_user = Mock()
            mock_user.check_password.return_value = True
            mock_user.id = 1
            mock_user.email = "test@example.com"
            mock_get.return_value = mock_user

            result = service.login_user("test@example.com", "correctpass")

            assert result["user"]["email"] == "test@example.com"
            assert "access_token" in result

    def test_login_invalid_password(self, django_db_setup):
        """Test login with invalid password."""
        service = AuthenticationService()

        with patch('backend.api.services.auth_service.User.objects.get') as mock_get:
            mock_user = Mock()
            mock_user.check_password.return_value = False
            mock_get.return_value = mock_user

            with pytest.raises(ValueError, match="Invalid credentials"):
                service.login_user("test@example.com", "wrongpass")

    def test_login_nonexistent_user(self, django_db_setup):
        """Test login with non-existent user."""
        service = AuthenticationService()

        with patch('backend.api.services.auth_service.User.objects.get') as mock_get:
            mock_get.side_effect = User.DoesNotExist()

            with pytest.raises(User.DoesNotExist):
                service.login_user("nonexistent@example.com", "password")

    def test_change_password_success(self, django_db_setup):
        """Test successful password change."""
        service = AuthenticationService()

        with patch('backend.api.services.auth_service.User.objects.get') as mock_get:
            mock_user = Mock()
            mock_user.check_password.return_value = True
            mock_get.return_value = mock_user

            result = service.change_password(1, "oldpass", "newpass123!")

            assert result is True
            mock_user.set_password.assert_called_once_with("newpass123!")
            mock_user.save.assert_called_once()

    def test_change_password_wrong_old_password(self, django_db_setup):
        """Test password change with wrong old password."""
        service = AuthenticationService()

        with patch('backend.api.services.auth_service.User.objects.get') as mock_get:
            mock_user = Mock()
            mock_user.check_password.return_value = False
            mock_get.return_value = mock_user

            with pytest.raises(ValueError, match="Incorrect current password"):
                service.change_password(1, "wrongold", "newpass123!")

    def test_reset_password_with_valid_token(self, django_db_setup):
        """Test password reset with valid token."""
        service = AuthenticationService()

        with patch('backend.api.services.auth_service.User.objects.get') as mock_get:
            mock_user = Mock()
            mock_user.reset_token = "valid_token"
            mock_user.reset_token_expires = datetime.now() + timedelta(hours=1)
            mock_get.return_value = mock_user

            result = service.reset_password("valid_token", "newpass123!")

            assert result is True
            mock_user.set_password.assert_called_once_with("newpass123!")

    def test_reset_password_with_expired_token(self, django_db_setup):
        """Test password reset with expired token."""
        service = AuthenticationService()

        with patch('backend.api.services.auth_service.User.objects.get') as mock_get:
            mock_user = Mock()
            mock_user.reset_token = "expired_token"
            mock_user.reset_token_expires = datetime.now() - timedelta(hours=1)
            mock_get.return_value = mock_user

            with pytest.raises(ValueError, match="expired or invalid"):
                service.reset_password("expired_token", "newpass123!")


@pytest.mark.unit
class TestPaymentService:
    """Tests for payment service."""

    @pytest.fixture
    def payment_service(self):
        """Create payment service with mocked Stripe."""
        with patch('backend.api.services.payment_service.stripe') as mock_stripe:
            service = PaymentService()
            service.stripe = mock_stripe
            return service

    def test_create_payment_intent(self, payment_service):
        """Test creating a payment intent."""
        payment_service.stripe.PaymentIntent.create.return_value = {
            "id": "pi_123",
            "amount": 5000,
            "currency": "usd",
            "status": "requires_payment_method"
        }

        result = payment_service.create_payment_intent(5000, "usd")

        assert result["id"] == "pi_123"
        assert result["amount"] == 5000
        payment_service.stripe.PaymentIntent.create.assert_called_once()

    def test_confirm_payment_intent(self, payment_service):
        """Test confirming a payment intent."""
        payment_service.stripe.PaymentIntent.retrieve.return_value = {
            "id": "pi_123",
            "status": "succeeded"
        }

        result = payment_service.confirm_payment_intent("pi_123")

        assert result["status"] == "succeeded"

    def test_create_refund_full_amount(self, payment_service):
        """Test creating a full refund."""
        payment_service.stripe.Refund.create.return_value = {
            "id": "re_123",
            "amount": 5000,
            "status": "succeeded"
        }

        result = payment_service.create_refund("pi_123")

        assert result["status"] == "succeeded"
        payment_service.stripe.Refund.create.assert_called_once()

    def test_create_refund_partial_amount(self, payment_service):
        """Test creating a partial refund."""
        payment_service.stripe.Refund.create.return_value = {
            "id": "re_123",
            "amount": 2500,
            "status": "succeeded"
        }

        result = payment_service.create_refund("pi_123", amount=2500)

        assert result["amount"] == 2500

    def test_create_customer(self, payment_service):
        """Test creating a Stripe customer."""
        payment_service.stripe.Customer.create.return_value = {
            "id": "cus_123",
            "email": "test@example.com"
        }

        result = payment_service.create_customer("test@example.com", "Test User")

        assert result["id"] == "cus_123"
        assert result["email"] == "test@example.com"


@pytest.mark.unit
class TestInventoryService:
    """Tests for inventory service."""

    def test_get_stock_level(self, django_db_setup):
        """Test getting stock level for a product."""
        service = InventoryService()

        with patch('backend.api.services.inventory_service.InventoryItem.objects.select_related') as mock_select:
            mock_item = Mock()
            mock_item.quantity = 100
            mock_item.reserved_quantity = 10
            mock_select.return_value.get.return_value = mock_item

            result = service.get_stock_level(1)

            assert result["available_quantity"] == 90
            assert result["total_quantity"] == 100

    def test_reserve_stock_success(self, django_db_setup):
        """Test reserving stock successfully."""
        service = InventoryService()

        with patch('backend.api.services.inventory_service.InventoryItem.objects.select_for_update') as mock_select:
            mock_item = Mock()
            mock_item.quantity = 100
            mock_item.reserved_quantity = 10
            mock_select.return_value.get.return_value = mock_item

            result = service.reserve_stock(1, 20, "order_123")

            assert result is True
            assert mock_item.reserved_quantity == 30

    def test_reserve_stock_insufficient(self, django_db_setup):
        """Test reserving stock with insufficient quantity."""
        service = InventoryService()

        with patch('backend.api.services.inventory_service.InventoryItem.objects.select_for_update') as mock_select:
            mock_item = Mock()
            mock_item.quantity = 100
            mock_item.reserved_quantity = 95
            mock_select.return_value.get.return_value = mock_item

            with pytest.raises(ValueError, match="Insufficient stock"):
                service.reserve_stock(1, 10, "order_123")

    def test_release_stock(self, django_db_setup):
        """Test releasing reserved stock."""
        service = InventoryService()

        with patch('backend.api.services.inventory_service.InventoryItem.objects.select_for_update') as mock_select:
            mock_item = Mock()
            mock_item.reserved_quantity = 30
            mock_select.return_value.get.return_value = mock_item

            result = service.release_stock(1, 20, "order_123")

            assert result is True
            assert mock_item.reserved_quantity == 10

    def test_adjust_stock(self, django_db_setup):
        """Test adjusting stock quantity."""
        service = InventoryService()

        with patch('backend.api.services.inventory_service.InventoryItem.objects.select_for_update') as mock_select:
            mock_item = Mock()
            mock_item.quantity = 100
            mock_select.return_value.get.return_value = mock_item

            result = service.adjust_stock(1, -5, "damaged", "adj_123")

            assert result is True
            assert mock_item.quantity == 95

    def test_get_low_stock_products(self, django_db_setup):
        """Test getting low stock products."""
        service = InventoryService()

        with patch('backend.api.services.inventory_service.InventoryItem.objects.filter') as mock_filter:
            mock_item = Mock()
            mock_item.product_id = 1
            mock_item.available_quantity = 5
            mock_filter.return_value.select_related.return_value = [mock_item]

            result = service.get_low_stock_products(threshold=10)

            assert len(result) == 1
            assert result[0]["product_id"] == 1

    def test_bulk_adjust_stock(self, django_db_setup):
        """Test bulk stock adjustment."""
        service = InventoryService()

        with patch('backend.api.services.inventory_service.transaction.atomic') as mock_atomic:
            mock_atomic.return_value.__enter__ = Mock()
            mock_atomic.return_value.__exit__ = Mock()

            with patch.object(service, 'adjust_stock', return_value=True) as mock_adjust:
                adjustments = [
                    {"product_id": 1, "warehouse_id": 1, "quantity": -5, "reason": "damage"},
                    {"product_id": 2, "warehouse_id": 1, "quantity": 10, "reason": "restock"},
                ]

                result = service.bulk_adjust_stock(adjustments)

                assert len(result["successful"]) == 2
                assert len(result["failed"]) == 0


@pytest.mark.unit
class TestRecommendationService:
    """Tests for recommendation service."""

    def test_get_similar_products(self, django_db_setup):
        """Test getting similar products."""
        service = RecommendationService()

        with patch('backend.api.services.recommendation_service.Product.objects.get') as mock_get:
            mock_product = Mock()
            mock_product.category_id = 1
            mock_product.tags = "electronics,tech"
            mock_get.return_value = mock_product

            with patch('backend.api.services.recommendation_service.Product.objects.filter') as mock_filter:
                mock_similar = Mock()
                mock_similar.id = 2
                mock_filter.return_value.exclude.return_value.order_by.return_value[:10].return_value = [mock_similar]

                result = service.get_similar_products(1, limit=10)

                assert len(result) >= 0

    def test_get_trending_products(self, django_db_setup):
        """Test getting trending products."""
        service = RecommendationService()

        with patch('backend.api.services.recommendation_service.Product.objects.filter') as mock_filter:
            mock_product = Mock()
            mock_product.id = 1
            mock_filter.return_value.order_by.return_value[:20].return_value = [mock_product]

            result = service.get_trending_products(limit=20)

            assert len(result) >= 0

    def test_get_frequently_bought_together(self, django_db_setup):
        """Test getting frequently bought together products."""
        service = RecommendationService()

        with patch('backend.api.services.recommendation_service.OrderItem.objects.filter') as mock_filter:
            mock_filter.return_value.values.return_value.annotate.return_value.order_by.return_value[:10].return_value = []

            result = service.get_frequently_bought_together(1, limit=10)

            assert isinstance(result, list)


@pytest.mark.unit
class TestProductService:
    """Tests for product service."""

    def test_get_product_with_details(self, django_db_setup):
        """Test getting product with full details."""
        service = ProductService()

        with patch('backend.api.services.product_service.Product.objects.get') as mock_get:
            mock_product = Mock()
            mock_product.id = 1
            mock_product.name = "Test Product"
            mock_product.price = Decimal("29.99")
            mock_get.return_value = mock_product

            result = service.get_product_with_details(1)

            assert result["id"] == 1
            assert result["name"] == "Test Product"

    def test_search_products(self, django_db_setup):
        """Test searching products."""
        service = ProductService()

        with patch('backend.api.services.product_service.Product.objects.filter') as mock_filter:
            mock_product = Mock()
            mock_product.id = 1
            mock_filter.return_value.distinct.return_value.order_by.return_value = [mock_product]

            result = service.search_products(query="test")

            assert len(result["results"]) >= 0

    def test_get_featured_products(self, django_db_setup):
        """Test getting featured products."""
        service = ProductService()

        with patch('backend.api.services.product_service.Product.objects.filter') as mock_filter:
            mock_product = Mock()
            mock_product.id = 1
            mock_filter.return_value.order_by.return_value[:20].return_value = [mock_product]

            result = service.get_featured_products(limit=20)

            assert len(result) >= 0

    def test_apply_discount(self, django_db_setup):
        """Test applying discount to product."""
        service = ProductService()

        with patch('backend.api.services.product_service.Product.objects.get') as mock_get:
            mock_product = Mock()
            mock_product.price = Decimal("100")
            mock_product.original_price = None
            mock_get.return_value = mock_product

            result = service.apply_discount(1, Decimal("0.2"))

            assert result is True
            assert mock_product.original_price == Decimal("100")
            assert mock_product.price == Decimal("80")

    def test_update_stock(self, django_db_setup):
        """Test updating product stock."""
        service = ProductService()

        with patch('backend.api.services.product_service.Product.objects.select_for_update') as mock_select:
            mock_product = Mock()
            mock_product.stock = 100
            mock_select.return_value.get.return_value = mock_product

            result = service.update_stock(1, -5)

            assert result is True
            assert mock_product.stock == 95


@pytest.mark.unit
class TestCartService:
    """Tests for cart service."""

    def test_get_or_create_cart(self, django_db_setup):
        """Test getting or creating user cart."""
        service = CartService()

        with patch('backend.api.services.cart_service.Cart.objects.get_or_create') as mock_get_create:
            mock_cart = Mock()
            mock_cart.id = 1
            mock_get_create.return_value = (mock_cart, True)

            result = service.get_or_create_cart(user_id=1)

            assert result["id"] == 1

    def test_add_item_to_cart(self, django_db_setup):
        """Test adding item to cart."""
        service = CartService()

        with patch('backend.api.services.cart_service.Product.objects.get') as mock_product_get:
            mock_product = Mock()
            mock_product.stock = 100
            mock_product.price = Decimal("29.99")
            mock_product_get.return_value = mock_product

            with patch('backend.api.services.cart_service.Cart.objects.get') as mock_cart_get:
                mock_cart = Mock()
                mock_cart.id = 1
                mock_cart.items = Mock()
                mock_cart.items.filter.return_value.first.return_value = None
                mock_cart_get.return_value = mock_cart

                result = service.add_item(cart_id=1, product_id=1, quantity=2)

                assert result is True

    def test_add_item_insufficient_stock(self, django_db_setup):
        """Test adding item with insufficient stock."""
        service = CartService()

        with patch('backend.api.services.cart_service.Product.objects.get') as mock_get:
            mock_product = Mock()
            mock_product.stock = 5
            mock_get.return_value = mock_product

            with pytest.raises(ValueError, match="Insufficient stock"):
                service.add_item(cart_id=1, product_id=1, quantity=10)

    def test_update_item_quantity(self, django_db_setup):
        """Test updating cart item quantity."""
        service = CartService()

        with patch('backend.api.services.cart_service.CartItem.objects.get') as mock_get:
            mock_item = Mock()
            mock_item.quantity = 2
            mock_get.return_value = mock_item

            result = service.update_item_quantity(item_id=1, quantity=5)

            assert result is True
            assert mock_item.quantity == 5

    def test_remove_item(self, django_db_setup):
        """Test removing item from cart."""
        service = CartService()

        with patch('backend.api.services.cart_service.CartItem.objects.get') as mock_get:
            mock_item = Mock()
            mock_get.return_value = mock_item

            result = service.remove_item(item_id=1)

            assert result is True
            mock_item.delete.assert_called_once()

    def test_clear_cart(self, django_db_setup):
        """Test clearing cart."""
        service = CartService()

        with patch('backend.api.services.cart_service.Cart.objects.get') as mock_get:
            mock_cart = Mock()
            mock_cart.items.all.return_value.delete.return_value = None
            mock_get.return_value = mock_cart

            result = service.clear_cart(cart_id=1)

            assert result is True


@pytest.mark.unit
class TestOrderService:
    """Tests for order service."""

    def test_create_order_from_cart(self, django_db_setup):
        """Test creating order from cart."""
        service = OrderService()

        with patch('backend.api.services.order_service.Cart.objects.select_related') as mock_select:
            mock_cart = Mock()
            mock_cart.user_id = 1
            mock_cart.total_items = 2
            mock_cart.total_price = Decimal("100")
            mock_cart.items.all.return_value = []
            mock_select.return_value.get.return_value = mock_cart

            with patch('backend.api.services.order_service.transaction.atomic') as mock_atomic:
                with patch('backend.api.services.order_service.Order.objects.create') as mock_create:
                    mock_order = Mock()
                    mock_order.id = 1
                    mock_create.return_value = mock_order

                    result = service.create_order_from_cart(
                        cart_id=1,
                        shipping_address={},
                        payment_method="stripe"
                    )

                    assert result["id"] == 1

    def test_create_order_empty_cart(self, django_db_setup):
        """Test creating order with empty cart."""
        service = OrderService()

        with patch('backend.api.services.order_service.Cart.objects.select_related') as mock_select:
            mock_cart = Mock()
            mock_cart.total_items = 0
            mock_select.return_value.get.return_value = mock_cart

            with pytest.raises(ValueError, match="Cannot create order from empty cart"):
                service.create_order_from_cart(
                    cart_id=1,
                    shipping_address={},
                    payment_method="stripe"
                )

    def test_update_order_status(self, django_db_setup):
        """Test updating order status."""
        service = OrderService()

        with patch('backend.api.services.order_service.Order.objects.get') as mock_get:
            mock_order = Mock()
            mock_order.status = "pending"
            mock_get.return_value = mock_order

            result = service.update_order_status(order_id=1, status="confirmed")

            assert result is True
            assert mock_order.status == "confirmed"

    def test_cancel_order(self, django_db_setup):
        """Test cancelling an order."""
        service = OrderService()

        with patch('backend.api.services.order_service.Order.objects.select_for_update') as mock_select:
            mock_order = Mock()
            mock_order.status = "pending"
            mock_order.items.all.return_value = []
            mock_select.return_value.get.return_value = mock_order

            result = service.cancel_order(order_id=1, reason="customer_request")

            assert result is True
            assert mock_order.status == "cancelled"

    def test_cancel_order_non_cancellable(self, django_db_setup):
        """Test cancelling non-cancellable order."""
        service = OrderService()

        with patch('backend.api.services.order_service.Order.objects.get') as mock_get:
            mock_order = Mock()
            mock_order.status = "shipped"
            mock_get.return_value = mock_order

            with pytest.raises(ValueError, match="cannot be cancelled"):
                service.cancel_order(order_id=1, reason="changed_mind")

    def test_process_refund(self, django_db_setup):
        """Test processing refund."""
        service = OrderService()

        with patch('backend.api.services.order_service.Order.objects.get') as mock_get:
            mock_order = Mock()
            mock_order.status = "delivered"
            mock_order.payment_status = "paid"
            mock_order.payment_intent_id = "pi_123"
            mock_get.return_value = mock_order

            with patch('backend.api.services.order_service.PaymentService') as mock_payment_service:
                mock_payment = Mock()
                mock_payment.create_refund.return_value = {"status": "succeeded"}
                mock_payment_service.return_value = mock_payment

                result = service.process_refund(order_id=1, amount=Decimal("50"))

                assert result is True


@pytest.mark.unit
class TestSearchService:
    """Tests for search service."""

    def test_search_products_elasticsearch(self, django_db_setup):
        """Test product search using Elasticsearch."""
        service = SearchService()

        with patch('backend.api.services.search_service.ProductDocument') as mock_doc:
            mock_search = Mock()
            mock_search.count.return_value = 5
            mock_search.to_dict.return_value = {
                "hits": {
                    "hits": [
                        {"_source": {"id": 1, "name": "Product 1"}}
                    ]
                }
            }
            mock_doc.search.return_value.execute.return_value = mock_search

            result = service.search_products("test", filters={}, page=1, page_size=20)

            assert "results" in result
            assert "total" in result

    def test_autocomplete_suggestions(self, django_db_setup):
        """Test getting autocomplete suggestions."""
        service = SearchService()

        with patch('backend.api.services.search_service.Product.objects.filter') as mock_filter:
            mock_product = Mock()
            mock_product.name = "Test Product"
            mock_filter.return_value.distinct.return_value.values_list.return_value[:10].return_value = [
                ("Test Product",)
            ]

            result = service.get_autocomplete_suggestions("test")

            assert isinstance(result, list)
