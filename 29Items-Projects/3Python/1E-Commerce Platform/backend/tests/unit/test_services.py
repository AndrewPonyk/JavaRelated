"""Unit tests for service layer."""
import pytest
from decimal import Decimal
from unittest.mock import Mock, patch

from apps.cart.services import CartService
from apps.checkout.services import CouponService


@pytest.mark.django_db
class TestCartService:
    """Tests for CartService."""

    def test_calculate_cart_totals(self, cart_factory, product_factory):
        """Test cart total calculations."""
        from apps.cart.models import CartItem

        cart = cart_factory()
        product = product_factory(price=Decimal('100.00'))

        CartItem.objects.create(
            cart=cart,
            product=product,
            quantity=2,
            unit_price=product.price
        )

        totals = CartService.calculate_cart_totals(cart)

        assert totals['subtotal'] == Decimal('200.00')
        assert 'tax_amount' in totals
        assert 'shipping_estimate' in totals
        assert 'total' in totals

    def test_calculate_free_shipping(self, cart_factory, product_factory):
        """Test free shipping for orders over $50."""
        from apps.cart.models import CartItem

        cart = cart_factory()
        product = product_factory(price=Decimal('60.00'))

        CartItem.objects.create(
            cart=cart,
            product=product,
            quantity=1,
            unit_price=product.price
        )

        totals = CartService.calculate_cart_totals(cart)
        assert totals['shipping_estimate'] == Decimal('0.00')

    def test_calculate_shipping_fee(self, cart_factory, product_factory):
        """Test shipping fee for orders under $50."""
        from apps.cart.models import CartItem

        cart = cart_factory()
        product = product_factory(price=Decimal('30.00'))

        CartItem.objects.create(
            cart=cart,
            product=product,
            quantity=1,
            unit_price=product.price
        )

        totals = CartService.calculate_cart_totals(cart)
        assert totals['shipping_estimate'] > Decimal('0.00')

    def test_add_item_to_cart(self, user_factory, product_factory):
        """Test adding item to cart."""
        user = user_factory()
        product = product_factory(stock_quantity=10)

        cart = CartService.add_item(user, product.id, 2)

        assert cart.item_count == 2
        assert cart.items.filter(product=product).exists()

    def test_add_item_insufficient_stock(self, user_factory, product_factory):
        """Test adding item when stock is insufficient."""
        user = user_factory()
        product = product_factory(stock_quantity=5)

        with pytest.raises(ValueError, match="Insufficient stock"):
            CartService.add_item(user, product.id, 10)

    def test_update_item_quantity(self, cart_factory, product_factory):
        """Test updating cart item quantity."""
        from apps.cart.models import CartItem

        cart = cart_factory()
        product = product_factory(stock_quantity=20)

        item = CartItem.objects.create(
            cart=cart,
            product=product,
            quantity=2,
            unit_price=product.price
        )

        updated_cart = CartService.update_item_quantity(cart.user, item.id, 5)
        item.refresh_from_db()

        assert item.quantity == 5

    def test_remove_item(self, cart_factory, product_factory):
        """Test removing item from cart."""
        from apps.cart.models import CartItem

        cart = cart_factory()
        product = product_factory()

        item = CartItem.objects.create(
            cart=cart,
            product=product,
            quantity=1,
            unit_price=product.price
        )

        CartService.remove_item(cart.user, item.id)

        assert not CartItem.objects.filter(id=item.id).exists()

    def test_clear_cart(self, cart_factory, product_factory):
        """Test clearing entire cart."""
        from apps.cart.models import CartItem

        cart = cart_factory()
        product1 = product_factory()
        product2 = product_factory()

        CartItem.objects.create(cart=cart, product=product1, quantity=1, unit_price=product1.price)
        CartItem.objects.create(cart=cart, product=product2, quantity=2, unit_price=product2.price)

        CartService.clear_cart(cart.user)

        assert cart.items.count() == 0


@pytest.mark.django_db
class TestCouponService:
    """Tests for CouponService."""

    def test_calculate_percentage_discount(self, db):
        """Test percentage discount calculation."""
        from apps.checkout.models import Coupon
        from django.utils import timezone
        from datetime import timedelta

        coupon = Coupon.objects.create(
            code='SAVE20',
            discount_type=Coupon.DiscountType.PERCENTAGE,
            discount_value=Decimal('20.00'),
            valid_from=timezone.now() - timedelta(days=1),
            valid_until=timezone.now() + timedelta(days=1)
        )

        cart_total = Decimal('100.00')
        discount = CouponService.calculate_discount(coupon, cart_total)

        assert discount == Decimal('20.00')

    def test_calculate_fixed_discount(self, db):
        """Test fixed amount discount calculation."""
        from apps.checkout.models import Coupon
        from django.utils import timezone
        from datetime import timedelta

        coupon = Coupon.objects.create(
            code='FLAT10',
            discount_type=Coupon.DiscountType.FIXED,
            discount_value=Decimal('10.00'),
            valid_from=timezone.now() - timedelta(days=1),
            valid_until=timezone.now() + timedelta(days=1)
        )

        cart_total = Decimal('50.00')
        discount = CouponService.calculate_discount(coupon, cart_total)

        assert discount == Decimal('10.00')

    def test_maximum_discount_cap(self, db):
        """Test maximum discount amount cap."""
        from apps.checkout.models import Coupon
        from django.utils import timezone
        from datetime import timedelta

        coupon = Coupon.objects.create(
            code='BIG50',
            discount_type=Coupon.DiscountType.PERCENTAGE,
            discount_value=Decimal('50.00'),
            maximum_discount_amount=Decimal('25.00'),
            valid_from=timezone.now() - timedelta(days=1),
            valid_until=timezone.now() + timedelta(days=1)
        )

        cart_total = Decimal('100.00')
        discount = CouponService.calculate_discount(coupon, cart_total)

        # 50% of 100 = 50, but capped at 25
        assert discount == Decimal('25.00')

    def test_validate_coupon_expired(self, db):
        """Test validation of expired coupon."""
        from apps.checkout.models import Coupon
        from django.utils import timezone
        from datetime import timedelta

        coupon = Coupon.objects.create(
            code='EXPIRED',
            discount_type=Coupon.DiscountType.PERCENTAGE,
            discount_value=Decimal('10.00'),
            valid_from=timezone.now() - timedelta(days=30),
            valid_until=timezone.now() - timedelta(days=1)
        )

        result = CouponService.validate_coupon('EXPIRED', Decimal('100.00'))
        assert result['valid'] is False

    def test_validate_coupon_minimum_purchase(self, db):
        """Test coupon minimum purchase validation."""
        from apps.checkout.models import Coupon
        from django.utils import timezone
        from datetime import timedelta

        coupon = Coupon.objects.create(
            code='MIN100',
            discount_type=Coupon.DiscountType.FIXED,
            discount_value=Decimal('10.00'),
            minimum_purchase_amount=Decimal('100.00'),
            valid_from=timezone.now() - timedelta(days=1),
            valid_until=timezone.now() + timedelta(days=1)
        )

        result = CouponService.validate_coupon('MIN100', Decimal('50.00'))
        assert result['valid'] is False

    def test_validate_valid_coupon(self, coupon_factory):
        """Test validation of valid coupon."""
        coupon = coupon_factory(code='VALID')

        result = CouponService.validate_coupon('VALID', Decimal('100.00'))
        assert result['valid'] is True


@pytest.mark.django_db
class TestUserService:
    """Tests for UserService."""

    def test_verify_email(self, user_factory, mock_cache):
        """Test email verification."""
        from apps.users.services import UserService

        user = user_factory(is_verified=False)

        # Mock the cache to return the user ID for the token
        with patch('django.core.cache.cache') as cache_mock:
            cache_mock.get.return_value = user.id

            result = UserService.verify_email('valid-token')

            assert result is True
            user.refresh_from_db()

    def test_change_password(self, user_factory):
        """Test password change."""
        from apps.users.services import UserService

        user = user_factory(password='oldpassword123')

        result = UserService.change_password(user, 'oldpassword123', 'newpassword456')

        assert result is True
        assert user.check_password('newpassword456')

    def test_change_password_wrong_old_password(self, user_factory):
        """Test password change with wrong old password."""
        from apps.users.services import UserService

        user = user_factory(password='oldpassword123')

        with pytest.raises(ValueError, match="incorrect"):
            UserService.change_password(user, 'wrongpassword', 'newpassword456')


@pytest.mark.django_db
class TestProductService:
    """Tests for ProductService."""

    def test_create_product_with_variants(self, vendor_factory, category_factory):
        """Test creating product with variants."""
        from apps.products.services import ProductService

        vendor = vendor_factory()
        category = category_factory()

        product = ProductService.create_product_with_variants(
            vendor=vendor,
            name='Test Product',
            category=category,
            price=Decimal('99.99'),
            description='Test description',
            variants=[
                {'name': 'Small', 'price_adjustment': Decimal('0.00')},
                {'name': 'Large', 'price_adjustment': Decimal('10.00')}
            ]
        )

        assert product.name == 'Test Product'
        assert product.variants.count() == 2

    def test_update_product_status(self, product_factory):
        """Test updating product status."""
        from apps.products.services import ProductService
        from apps.products.models import Product

        product = product_factory(status=Product.Status.ACTIVE)

        ProductService.update_product_status(product, Product.Status.INACTIVE)

        product.refresh_from_db()
        assert product.status == Product.Status.INACTIVE


@pytest.mark.django_db
class TestVendorService:
    """Tests for VendorService."""

    def test_approve_vendor(self, vendor_factory, mock_email):
        """Test approving vendor."""
        from apps.vendors.services import VendorService
        from apps.vendors.models import Vendor

        vendor = vendor_factory(status=Vendor.Status.PENDING)

        result = VendorService.approve_vendor(vendor.id)

        assert result.status == Vendor.Status.APPROVED

    def test_reject_vendor(self, vendor_factory, mock_email):
        """Test rejecting vendor."""
        from apps.vendors.services import VendorService
        from apps.vendors.models import Vendor

        vendor = vendor_factory(status=Vendor.Status.PENDING)

        result = VendorService.reject_vendor(vendor.id, 'Incomplete documentation')

        assert result.status == Vendor.Status.REJECTED

    def test_suspend_vendor_deactivates_products(self, vendor_factory, product_factory, mock_email):
        """Test suspending vendor deactivates products."""
        from apps.vendors.services import VendorService
        from apps.vendors.models import Vendor
        from apps.products.models import Product

        vendor = vendor_factory(status=Vendor.Status.APPROVED)
        product1 = product_factory(vendor=vendor, status=Product.Status.ACTIVE)
        product2 = product_factory(vendor=vendor, status=Product.Status.ACTIVE)

        VendorService.suspend_vendor(vendor.id, 'Policy violation')

        product1.refresh_from_db()
        product2.refresh_from_db()

        assert product1.status == Product.Status.INACTIVE
        assert product2.status == Product.Status.INACTIVE
