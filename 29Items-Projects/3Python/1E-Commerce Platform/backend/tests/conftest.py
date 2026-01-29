"""Pytest configuration and fixtures for E-Commerce Platform."""
import pytest
from decimal import Decimal
from django.contrib.auth import get_user_model
from django.utils import timezone
from datetime import timedelta
from rest_framework.test import APIClient
from rest_framework_simplejwt.tokens import RefreshToken

User = get_user_model()


@pytest.fixture
def api_client():
    """Return an unauthenticated API client."""
    return APIClient()


@pytest.fixture
def user_factory(db):
    """Factory for creating test users."""
    created_users = []

    def create_user(
        email=None,
        password='testpass123',
        first_name='Test',
        last_name='User',
        **kwargs
    ):
        import uuid
        if email is None:
            email = f'test-{uuid.uuid4().hex[:8]}@example.com'

        user = User.objects.create_user(
            email=email,
            password=password,
            first_name=first_name,
            last_name=last_name,
            **kwargs
        )
        created_users.append(user)
        return user

    return create_user


@pytest.fixture
def authenticated_client(api_client, user_factory):
    """Return an authenticated API client."""
    user = user_factory()
    api_client.force_authenticate(user=user)
    api_client.user = user
    return api_client


@pytest.fixture
def admin_user(db):
    """Create an admin user."""
    return User.objects.create_superuser(
        email='admin@test.com',
        password='adminpass123',
        first_name='Admin',
        last_name='User'
    )


@pytest.fixture
def admin_client(api_client, admin_user):
    """Return an API client authenticated as admin."""
    api_client.force_authenticate(user=admin_user)
    api_client.user = admin_user
    return api_client


@pytest.fixture
def vendor_factory(db, user_factory):
    """Factory for creating test vendors."""
    def create_vendor(
        user=None,
        business_name=None,
        **kwargs
    ):
        from apps.vendors.models import Vendor
        import uuid

        if business_name is None:
            business_name = f'Test Vendor {uuid.uuid4().hex[:6]}'

        if not user:
            user = user_factory(
                email=f'{uuid.uuid4().hex[:8]}@vendor.com',
                is_vendor=True
            )

        return Vendor.objects.create(
            user=user,
            business_name=business_name,
            slug=business_name.lower().replace(' ', '-') + f'-{uuid.uuid4().hex[:4]}',
            business_email=user.email,
            status=kwargs.pop('status', Vendor.Status.APPROVED),
            **kwargs
        )
    return create_vendor


@pytest.fixture
def vendor_user(user_factory, vendor_factory):
    """Create a user with an approved vendor profile."""
    user = user_factory(is_vendor=True)
    vendor_factory(user=user)
    return user


@pytest.fixture
def vendor_client(api_client, vendor_user):
    """Return an API client authenticated as vendor."""
    api_client.force_authenticate(user=vendor_user)
    api_client.user = vendor_user
    return api_client


@pytest.fixture
def category_factory(db):
    """Factory for creating test categories."""
    def create_category(name=None, **kwargs):
        from apps.products.models import Category
        import uuid

        if name is None:
            name = f'Category {uuid.uuid4().hex[:6]}'

        slug = kwargs.pop('slug', None)
        if slug is None:
            slug = name.lower().replace(' ', '-') + f'-{uuid.uuid4().hex[:4]}'

        return Category.objects.create(
            name=name,
            slug=slug,
            **kwargs
        )
    return create_category


@pytest.fixture
def product_factory(db, category_factory, vendor_factory):
    """Factory for creating test products."""
    def create_product(
        name=None,
        price=None,
        category=None,
        vendor=None,
        **kwargs
    ):
        from apps.products.models import Product
        from apps.inventory.models import InventoryItem
        import uuid

        if name is None:
            name = f'Product {uuid.uuid4().hex[:6]}'

        if price is None:
            price = Decimal('99.99')

        if not category:
            category = category_factory()
        if not vendor:
            vendor = vendor_factory()

        slug = kwargs.pop('slug', None)
        if slug is None:
            slug = name.lower().replace(' ', '-') + f'-{uuid.uuid4().hex[:8]}'

        sku = kwargs.pop('sku', None)
        if sku is None:
            sku = f'SKU-{uuid.uuid4().hex[:8].upper()}'

        stock_quantity = kwargs.pop('stock_quantity', 100)

        product = Product.objects.create(
            name=name,
            slug=slug,
            sku=sku,
            category=category,
            vendor=vendor,
            short_description=kwargs.pop('short_description', f'Short description for {name}'),
            description=kwargs.pop('description', f'Full description for {name}'),
            price=price,
            status=kwargs.pop('status', Product.Status.ACTIVE),
            **kwargs
        )

        # Create inventory item
        InventoryItem.objects.create(
            product=product,
            quantity=stock_quantity,
            reserved_quantity=0
        )

        return product
    return create_product


@pytest.fixture
def cart_factory(db, user_factory):
    """Factory for creating test carts."""
    def create_cart(user=None, **kwargs):
        from apps.cart.models import Cart

        if not user:
            user = user_factory()

        cart, _ = Cart.objects.get_or_create(user=user, defaults=kwargs)
        return cart
    return create_cart


@pytest.fixture
def order_factory(db, user_factory, product_factory):
    """Factory for creating test orders."""
    def create_order(
        user=None,
        products=None,
        **kwargs
    ):
        from apps.checkout.models import Order, OrderItem

        if not user:
            user = user_factory()

        subtotal = Decimal('0.00')
        order = Order.objects.create(
            user=user,
            email=user.email,
            subtotal=subtotal,
            tax_amount=Decimal('0.00'),
            shipping_amount=kwargs.pop('shipping_amount', Decimal('5.99')),
            total=Decimal('0.00'),
            shipping_address=kwargs.pop('shipping_address', {
                'street_address': '123 Test St',
                'city': 'Test City',
                'state': 'CA',
                'postal_code': '12345',
                'country': 'US'
            }),
            billing_address=kwargs.pop('billing_address', {
                'street_address': '123 Test St',
                'city': 'Test City',
                'state': 'CA',
                'postal_code': '12345',
                'country': 'US'
            }),
            **kwargs
        )

        # Add products
        if products:
            for item in products:
                product = item.get('product') or product_factory()
                quantity = item.get('quantity', 1)
                unit_price = product.price
                line_total = unit_price * quantity

                OrderItem.objects.create(
                    order=order,
                    product=product,
                    product_name=product.name,
                    product_sku=product.sku,
                    quantity=quantity,
                    unit_price=unit_price,
                    line_total=line_total
                )
                subtotal += line_total

        # Update totals
        order.subtotal = subtotal
        order.tax_amount = subtotal * Decimal('0.08')
        order.total = order.subtotal + order.tax_amount + order.shipping_amount
        order.save()

        return order
    return create_order


@pytest.fixture
def coupon_factory(db):
    """Factory for creating test coupons."""
    def create_coupon(
        code=None,
        discount_type=None,
        discount_value=None,
        **kwargs
    ):
        from apps.checkout.models import Coupon
        import uuid

        if code is None:
            code = f'CODE{uuid.uuid4().hex[:6].upper()}'

        if discount_type is None:
            discount_type = Coupon.DiscountType.PERCENTAGE

        if discount_value is None:
            discount_value = Decimal('10.00')

        return Coupon.objects.create(
            code=code,
            discount_type=discount_type,
            discount_value=discount_value,
            is_active=kwargs.pop('is_active', True),
            valid_from=kwargs.pop('valid_from', timezone.now() - timedelta(days=1)),
            valid_until=kwargs.pop('valid_until', timezone.now() + timedelta(days=30)),
            **kwargs
        )
    return create_coupon


@pytest.fixture
def address_factory(db, user_factory):
    """Factory for creating test addresses."""
    def create_address(user=None, **kwargs):
        from apps.users.models import Address

        if not user:
            user = user_factory()

        return Address.objects.create(
            user=user,
            address_type=kwargs.pop('address_type', Address.AddressType.SHIPPING),
            street_address=kwargs.pop('street_address', '123 Test Street'),
            city=kwargs.pop('city', 'Test City'),
            state=kwargs.pop('state', 'CA'),
            postal_code=kwargs.pop('postal_code', '12345'),
            country=kwargs.pop('country', 'US'),
            is_default=kwargs.pop('is_default', False),
            **kwargs
        )
    return create_address


# Database cleanup
@pytest.fixture(autouse=True)
def enable_db_access_for_all_tests(db):
    """Enable database access for all tests."""
    pass


# Mock external services
@pytest.fixture
def mock_stripe(mocker):
    """Mock Stripe API calls."""
    mock_pi = mocker.patch('stripe.PaymentIntent')
    mock_pi.create.return_value = mocker.Mock(
        id='pi_test_123',
        client_secret='pi_test_123_secret_xxx',
        status='requires_payment_method'
    )
    mock_pi.retrieve.return_value = mocker.Mock(
        id='pi_test_123',
        status='succeeded',
        amount=10000,
        currency='usd'
    )
    mock_pi.cancel.return_value = mocker.Mock(
        id='pi_test_123',
        status='canceled'
    )

    mock_refund = mocker.patch('stripe.Refund')
    mock_refund.create.return_value = mocker.Mock(
        id='re_test_123',
        status='succeeded'
    )

    return {
        'PaymentIntent': mock_pi,
        'Refund': mock_refund
    }


@pytest.fixture
def mock_elasticsearch(mocker):
    """Mock Elasticsearch calls."""
    mock_search = mocker.patch('elasticsearch_dsl.Search.execute')
    mock_search.return_value = mocker.Mock(
        hits=mocker.Mock(total=mocker.Mock(value=0))
    )
    mock_search.return_value.__iter__ = lambda self: iter([])
    return mock_search


@pytest.fixture
def mock_celery(mocker):
    """Mock Celery task execution."""
    mocker.patch('celery.app.task.Task.delay')
    mocker.patch('celery.app.task.Task.apply_async')


@pytest.fixture
def mock_email(mocker):
    """Mock email sending."""
    return mocker.patch('django.core.mail.send_mail')


@pytest.fixture
def mock_cache(mocker):
    """Mock Django cache."""
    cache_data = {}

    def mock_get(key, default=None):
        return cache_data.get(key, default)

    def mock_set(key, value, timeout=None):
        cache_data[key] = value

    def mock_delete(key):
        cache_data.pop(key, None)

    mock = mocker.patch('django.core.cache.cache')
    mock.get = mock_get
    mock.set = mock_set
    mock.delete = mock_delete
    return mock
