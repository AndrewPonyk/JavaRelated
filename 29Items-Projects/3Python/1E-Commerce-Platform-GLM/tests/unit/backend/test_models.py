"""
Unit Tests for Models

Tests for Django model methods and properties.
"""

import pytest
from decimal import Decimal
from datetime import datetime, timedelta
from django.core.exceptions import ValidationError

from backend.core.models.user import User
from backend.core.models.product import Product, Category, ProductImage
from backend.core.models.vendor import Vendor
from backend.core.models.cart import Cart, CartItem
from backend.core.models.order import Order, OrderItem


@pytest.mark.django_db
@pytest.mark.unit
class TestUserModel:
    """Tests for User model."""

    def test_create_user(self):
        """Test creating a regular user."""
        user = User.objects.create_user(
            email="user@example.com",
            password="testpass123",
            first_name="Test",
            last_name="User"
        )

        assert user.email == "user@example.com"
        assert user.first_name == "Test"
        assert user.is_staff is False
        assert user.is_superuser is False
        assert user.check_password("testpass123") is True

    def test_create_superuser(self):
        """Test creating a superuser."""
        user = User.objects.create_superuser(
            email="admin@example.com",
            password="adminpass123"
        )

        assert user.is_staff is True
        assert user.is_superuser is True

    def test_user_str(self):
        """Test user string representation."""
        user = User.objects.create_user(
            email="test@example.com",
            password="testpass"
        )

        assert str(user) == "test@example.com"

    def test_user_full_name(self):
        """Test user full name property."""
        user = User.objects.create_user(
            email="test@example.com",
            password="testpass",
            first_name="John",
            last_name="Doe"
        )

        assert user.full_name == "John Doe"

    def test_user_vendor_profile_relationship(self):
        """Test user to vendor profile relationship."""
        from backend.core.models import Vendor

        vendor = Vendor.objects.create(
            name="Test Vendor",
            slug="test-vendor",
            business_name="Test Vendor Inc",
            business_type="company",
            email="vendor@example.com",
            phone="+1234567890"
        )

        user = User.objects.create_user(
            email="vendoruser@example.com",
            password="testpass",
            is_vendor=True
        )
        user.vendor_profile = vendor
        user.save()

        assert user.vendor_profile.name == "Test Vendor"


@pytest.mark.django_db
@pytest.mark.unit
class TestCategoryModel:
    """Tests for Category model."""

    def test_create_category(self):
        """Test creating a category."""
        category = Category.objects.create(
            name="Electronics",
            slug="electronics",
            description="Electronic devices"
        )

        assert category.name == "Electronics"
        assert category.slug == "electronics"

    def test_category_parent_child(self):
        """Test category parent-child relationship."""
        parent = Category.objects.create(
            name="Computers",
            slug="computers"
        )

        child = Category.objects.create(
            name="Laptops",
            slug="laptops",
            parent=parent
        )

        assert child.parent == parent
        assert list(parent.children.all()) == [child]

    def test_category_str(self):
        """Test category string representation."""
        category = Category.objects.create(
            name="Electronics",
            slug="electronics"
        )

        assert str(category) == "Electronics"


@pytest.mark.django_db
@pytest.mark.unit
class TestVendorModel:
    """Tests for Vendor model."""

    def test_create_vendor(self):
        """Test creating a vendor."""
        vendor = Vendor.objects.create(
            name="Test Vendor",
            slug="test-vendor",
            business_name="Test Vendor Inc",
            business_type="company",
            email="vendor@example.com",
            phone="+1234567890"
        )

        assert vendor.name == "Test Vendor"
        assert vendor.business_type == "company"

    def test_vendor_str(self):
        """Test vendor string representation."""
        vendor = Vendor.objects.create(
            name="Test Vendor",
            slug="test-vendor"
        )

        assert str(vendor) == "Test Vendor"

    def test_vendor_rating_calculation(self):
        """Test vendor rating calculation."""
        vendor = Vendor.objects.create(
            name="Test Vendor",
            slug="test-vendor",
            total_products=10,
            completed_orders=100,
            total_revenue=Decimal("10000.00")
        )

        vendor.calculate_rating(4.5, 50)

        assert vendor.average_rating == 4.5
        assert vendor.review_count == 50


@pytest.mark.django_db
@pytest.mark.unit
class TestProductModel:
    """Tests for Product model."""

    def test_create_product(self):
        """Test creating a product."""
        category = Category.objects.create(name="Electronics", slug="electronics")
        vendor = Vendor.objects.create(name="Test Vendor", slug="test-vendor")

        product = Product.objects.create(
            name="Test Product",
            slug="test-product",
            description="A test product",
            price=Decimal("29.99"),
            category=category,
            vendor=vendor,
            stock=100
        )

        assert product.name == "Test Product"
        assert product.price == Decimal("29.99")

    def test_product_discount_percentage(self):
        """Test product discount percentage calculation."""
        category = Category.objects.create(name="Test", slug="test")
        vendor = Vendor.objects.create(name="Vendor", slug="vendor")

        product = Product.objects.create(
            name="Product",
            slug="product",
            price=Decimal("80.00"),
            original_price=Decimal("100.00"),
            category=category,
            vendor=vendor
        )

        assert product.discount_percentage == 20

    def test_product_is_in_stock(self):
        """Test product in stock property."""
        category = Category.objects.create(name="Test", slug="test")
        vendor = Vendor.objects.create(name="Vendor", slug="vendor")

        product = Product.objects.create(
            name="Product",
            slug="product",
            price=Decimal("10.00"),
            category=category,
            vendor=vendor,
            stock=10
        )

        assert product.is_in_stock is True

        product.stock = 0
        product.save()

        assert product.is_in_stock is False

    def test_product_str(self):
        """Test product string representation."""
        category = Category.objects.create(name="Test", slug="test")
        vendor = Vendor.objects.create(name="Vendor", slug="vendor")

        product = Product.objects.create(
            name="Test Product",
            slug="test-product",
            price=Decimal("10.00"),
            category=category,
            vendor=vendor
        )

        assert str(product) == "Test Product"


@pytest.mark.django_db
@pytest.mark.unit
class TestProductImageModel:
    """Tests for ProductImage model."""

    def test_create_product_image(self):
        """Test creating a product image."""
        category = Category.objects.create(name="Test", slug="test")
        vendor = Vendor.objects.create(name="Vendor", slug="vendor")

        product = Product.objects.create(
            name="Product",
            slug="product",
            price=Decimal("10.00"),
            category=category,
            vendor=vendor
        )

        image = ProductImage.objects.create(
            product=product,
            image="products/test.jpg",
            alt_text="Test image"
        )

        assert image.product == product
        assert image.image == "products/test.jpg"

    # TODO: Uncomment when is_main field is added to ProductImage model
    # def test_main_image_singleton(self):
    #     """Test only one main image per product."""
    #     ... (test case)


@pytest.mark.django_db
@pytest.mark.unit
class TestCartModel:
    """Tests for Cart model."""

    def test_create_cart(self):
        """Test creating a cart."""
        user = User.objects.create_user(
            email="user@example.com",
            password="testpass"
        )

        cart = Cart.objects.create(user=user)

        assert cart.user == user
        assert cart.total_items == 0

    def test_cart_str(self):
        """Test cart string representation."""
        user = User.objects.create_user(
            email="user@example.com",
            password="testpass"
        )

        cart = Cart.objects.create(user=user)

        assert str(cart) == f"Cart for {user.email}"


@pytest.mark.django_db
@pytest.mark.unit
class TestCartItemModel:
    """Tests for CartItem model."""

    def test_create_cart_item(self):
        """Test creating a cart item."""
        user = User.objects.create_user(email="user@example.com", password="testpass")
        cart = Cart.objects.create(user=user)

        category = Category.objects.create(name="Test", slug="test")
        vendor = Vendor.objects.create(name="Vendor", slug="vendor")

        product = Product.objects.create(
            name="Product",
            slug="product",
            price=Decimal("29.99"),
            category=category,
            vendor=vendor
        )

        item = CartItem.objects.create(
            cart=cart,
            product=product,
            quantity=2,
            unit_price=Decimal("29.99")
        )

        assert item.cart == cart
        assert item.product == product
        assert item.quantity == 2
        assert item.subtotal == Decimal("59.98")

    def test_cart_item_subtotal(self):
        """Test cart item subtotal calculation."""
        user = User.objects.create_user(email="user@example.com", password="testpass")
        cart = Cart.objects.create(user=user)

        category = Category.objects.create(name="Test", slug="test")
        vendor = Vendor.objects.create(name="Vendor", slug="vendor")

        product = Product.objects.create(
            name="Product",
            slug="product",
            price=Decimal("25.00"),
            category=category,
            vendor=vendor
        )

        item = CartItem.objects.create(
            cart=cart,
            product=product,
            quantity=3,
            unit_price=Decimal("25.00")
        )

        assert item.subtotal == Decimal("75.00")


@pytest.mark.django_db
@pytest.mark.unit
class TestOrderModel:
    """Tests for Order model."""

    def test_create_order(self):
        """Test creating an order."""
        user = User.objects.create_user(email="user@example.com", password="testpass")

        category = Category.objects.create(name="Test", slug="test")
        vendor = Vendor.objects.create(name="Vendor", slug="vendor")

        order = Order.objects.create(
            user=user,
            order_number="ORD-001",
            status="pending",
            total_amount=Decimal("100.00"),
            currency="USD"
        )

        assert order.user == user
        assert order.order_number == "ORD-001"
        assert order.status == "pending"

    def test_order_number_generation(self):
        """Test automatic order number generation."""
        user = User.objects.create_user(email="user@example.com", password="testpass")

        order = Order.objects.create(
            user=user,
            total_amount=Decimal("100.00")
        )

        assert order.order_number is not None
        assert order.order_number.startswith("ORD-")

    def test_order_str(self):
        """Test order string representation."""
        user = User.objects.create_user(email="user@example.com", password="testpass")

        order = Order.objects.create(
            user=user,
            order_number="ORD-001",
            total_amount=Decimal("100.00")
        )

        assert str(order) == "ORD-001"

    def test_order_is_cancellable(self):
        """Test order cancellable status."""
        user = User.objects.create_user(email="user@example.com", password="testpass")

        order = Order.objects.create(
            user=user,
            total_amount=Decimal("100.00"),
            status="pending"
        )

        assert order.is_cancellable is True

        order.status = "shipped"
        order.save()

        assert order.is_cancellable is False


@pytest.mark.django_db
@pytest.mark.unit
class TestOrderItemModel:
    """Tests for OrderItem model."""

    def test_create_order_item(self):
        """Test creating an order item."""
        user = User.objects.create_user(email="user@example.com", password="testpass")

        category = Category.objects.create(name="Test", slug="test")
        vendor = Vendor.objects.create(name="Vendor", slug="vendor")

        product = Product.objects.create(
            name="Product",
            slug="product",
            price=Decimal("50.00"),
            category=category,
            vendor=vendor
        )

        order = Order.objects.create(
            user=user,
            total_amount=Decimal("100.00")
        )

        item = OrderItem.objects.create(
            order=order,
            product=product,
            quantity=2,
            unit_price=Decimal("50.00")
        )

        assert item.order == order
        assert item.product == product
        assert item.subtotal == Decimal("100.00")


# TODO: Uncomment when Address model is implemented
# @pytest.mark.django_db
# @pytest.mark.unit
# class TestAddressModel:
#     """Tests for Address model."""
#     ... (test cases)


# TODO: Uncomment and fix import when Review model is properly integrated
# @pytest.mark.django_db
# @pytest.mark.unit
# class TestReviewModel:
#     """Tests for Review model."""
#     ... (test cases)


# TODO: Uncomment these tests when models are implemented
# @pytest.mark.django_db
# @pytest.mark.unit
# class TestWishlistModel:
#     """Tests for Wishlist model."""
#     ... (test cases)
#
# @pytest.mark.django_db
# @pytest.mark.unit
# class TestWishlistItemModel:
#     """Tests for WishlistItem model."""
#     ... (test cases)
#
# @pytest.mark.django_db
# @pytest.mark.unit
# class TestCouponModel:
#     """Tests for Coupon model."""
#     ... (test cases)
#
# @pytest.mark.django_db
# @pytest.mark.unit
# class TestInventoryItemModel:
#     """Tests for InventoryItem model."""
#     ... (test cases)
#
# @pytest.mark.django_db
# @pytest.mark.unit
# class TestWarehouseModel:
#     """Tests for Warehouse model."""
#     ... (test cases)
#
# @pytest.mark.django_db
# @pytest.mark.unit
# class TestStockAlertModel:
#     """Tests for StockAlert model."""
#     ... (test cases)
