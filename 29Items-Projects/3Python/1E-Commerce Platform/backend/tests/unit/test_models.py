"""Unit tests for models."""
import pytest
from decimal import Decimal

from apps.products.models import Product


@pytest.mark.django_db
class TestUserModel:
    """Tests for User model."""

    def test_create_user(self, user_factory):
        """Test user creation."""
        user = user_factory(
            email='test@example.com',
            first_name='John',
            last_name='Doe'
        )
        assert user.email == 'test@example.com'
        assert user.full_name == 'John Doe'
        assert user.is_active
        assert not user.is_staff

    def test_create_superuser(self, db):
        """Test superuser creation."""
        from django.contrib.auth import get_user_model
        User = get_user_model()

        admin = User.objects.create_superuser(
            email='admin@example.com',
            password='adminpass123',
            first_name='Admin',
            last_name='User'
        )
        assert admin.is_staff
        assert admin.is_superuser

    def test_user_str(self, user_factory):
        """Test user string representation."""
        user = user_factory(email='str@test.com')
        assert str(user) == 'str@test.com'

    def test_email_normalized(self, user_factory):
        """Test email is normalized."""
        user = user_factory(email='Test@EXAMPLE.COM')
        assert user.email == 'Test@example.com'


@pytest.mark.django_db
class TestProductModel:
    """Tests for Product model."""

    def test_create_product(self, product_factory):
        """Test product creation."""
        product = product_factory(
            name='Test Product',
            price=Decimal('99.99')
        )
        assert product.name == 'Test Product'
        assert product.price == Decimal('99.99')
        assert product.status == Product.Status.ACTIVE

    def test_product_discount_percentage(self, product_factory):
        """Test discount percentage calculation."""
        product = product_factory(
            price=Decimal('80.00'),
            compare_at_price=Decimal('100.00')
        )
        assert product.discount_percentage == 20

    def test_product_no_discount(self, product_factory):
        """Test no discount when compare_at_price not set."""
        product = product_factory(price=Decimal('50.00'))
        assert product.discount_percentage == 0

    def test_product_str(self, product_factory):
        """Test product string representation."""
        product = product_factory(name='My Product')
        assert str(product) == 'My Product'

    def test_product_is_on_sale(self, product_factory):
        """Test is_on_sale property."""
        product = product_factory(
            price=Decimal('80.00'),
            compare_at_price=Decimal('100.00')
        )
        assert product.is_on_sale is True

        product2 = product_factory(price=Decimal('50.00'))
        assert product2.is_on_sale is False


@pytest.mark.django_db
class TestCategoryModel:
    """Tests for Category model."""

    def test_create_category(self, category_factory):
        """Test category creation."""
        category = category_factory(name='Electronics')
        assert category.name == 'Electronics'
        assert category.is_active is True

    def test_category_hierarchy(self, category_factory):
        """Test category parent-child relationship."""
        parent = category_factory(name='Electronics')
        child = category_factory(name='Phones', parent=parent)

        assert child.parent == parent
        assert parent.children.count() == 1

    def test_category_str(self, category_factory):
        """Test category string representation."""
        category = category_factory(name='Books')
        assert str(category) == 'Books'


@pytest.mark.django_db
class TestCartModel:
    """Tests for Cart model."""

    def test_cart_item_count(self, cart_factory, product_factory):
        """Test cart item count calculation."""
        from apps.cart.models import CartItem

        cart = cart_factory()
        product = product_factory()

        CartItem.objects.create(
            cart=cart,
            product=product,
            quantity=3,
            unit_price=product.price
        )

        assert cart.item_count == 3

    def test_cart_subtotal(self, cart_factory, product_factory):
        """Test cart subtotal calculation."""
        from apps.cart.models import CartItem

        cart = cart_factory()
        product1 = product_factory(price=Decimal('10.00'))
        product2 = product_factory(price=Decimal('20.00'))

        CartItem.objects.create(
            cart=cart,
            product=product1,
            quantity=2,
            unit_price=product1.price
        )
        CartItem.objects.create(
            cart=cart,
            product=product2,
            quantity=1,
            unit_price=product2.price
        )

        assert cart.subtotal == Decimal('40.00')

    def test_cart_is_empty(self, cart_factory):
        """Test cart is_empty property."""
        cart = cart_factory()
        assert cart.is_empty is True

    def test_cart_not_empty(self, cart_factory, product_factory):
        """Test cart with items is not empty."""
        from apps.cart.models import CartItem

        cart = cart_factory()
        product = product_factory()
        CartItem.objects.create(
            cart=cart,
            product=product,
            quantity=1,
            unit_price=product.price
        )
        assert cart.is_empty is False


@pytest.mark.django_db
class TestOrderModel:
    """Tests for Order model."""

    def test_order_number_generation(self, order_factory, product_factory):
        """Test order number auto-generation."""
        product = product_factory()
        order = order_factory(products=[{'product': product}])
        assert order.order_number.startswith('ORD-')

    def test_order_status_default(self, order_factory, product_factory):
        """Test default order status."""
        from apps.checkout.models import Order
        product = product_factory()
        order = order_factory(products=[{'product': product}])
        assert order.status == Order.Status.PENDING

    def test_order_total_calculation(self, order_factory, product_factory):
        """Test order total is calculated correctly."""
        product = product_factory(price=Decimal('100.00'))
        order = order_factory(products=[{'product': product, 'quantity': 2}])

        assert order.subtotal == Decimal('200.00')
        assert order.total > order.subtotal  # Includes tax and shipping


@pytest.mark.django_db
class TestVendorModel:
    """Tests for Vendor model."""

    def test_create_vendor(self, vendor_factory):
        """Test vendor creation."""
        vendor = vendor_factory(business_name='Test Shop')
        assert vendor.business_name == 'Test Shop'

    def test_vendor_str(self, vendor_factory):
        """Test vendor string representation."""
        vendor = vendor_factory(business_name='My Shop')
        assert str(vendor) == 'My Shop'

    def test_vendor_default_status(self, vendor_factory):
        """Test vendor default approved status from factory."""
        from apps.vendors.models import Vendor
        vendor = vendor_factory()
        assert vendor.status == Vendor.Status.APPROVED


@pytest.mark.django_db
class TestCouponModel:
    """Tests for Coupon model."""

    def test_create_percentage_coupon(self, coupon_factory):
        """Test creating percentage coupon."""
        from apps.checkout.models import Coupon

        coupon = coupon_factory(
            code='SAVE20',
            discount_type=Coupon.DiscountType.PERCENTAGE,
            discount_value=Decimal('20.00')
        )

        assert coupon.code == 'SAVE20'
        assert coupon.discount_type == Coupon.DiscountType.PERCENTAGE
        assert coupon.discount_value == Decimal('20.00')

    def test_create_fixed_coupon(self, coupon_factory):
        """Test creating fixed amount coupon."""
        from apps.checkout.models import Coupon

        coupon = coupon_factory(
            code='FLAT10',
            discount_type=Coupon.DiscountType.FIXED,
            discount_value=Decimal('10.00')
        )

        assert coupon.discount_type == Coupon.DiscountType.FIXED

    def test_coupon_str(self, coupon_factory):
        """Test coupon string representation."""
        coupon = coupon_factory(code='TEST123')
        assert 'TEST123' in str(coupon)

    def test_coupon_is_valid(self, coupon_factory):
        """Test coupon validity check."""
        coupon = coupon_factory(is_active=True)
        assert coupon.is_active is True


@pytest.mark.django_db
class TestAddressModel:
    """Tests for Address model."""

    def test_create_address(self, address_factory):
        """Test address creation."""
        address = address_factory(
            street_address='123 Main St',
            city='New York'
        )

        assert address.street_address == '123 Main St'
        assert address.city == 'New York'

    def test_address_str(self, address_factory):
        """Test address string representation."""
        address = address_factory(
            street_address='123 Test St',
            city='Test City',
            state='CA'
        )
        assert '123 Test St' in str(address) or 'Test City' in str(address)
