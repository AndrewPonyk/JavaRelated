"""User service layer for E-Commerce Platform."""
import secrets
from datetime import timedelta

from django.contrib.auth import get_user_model
from django.core.cache import cache
from django.db import transaction
from django.db.models import Count, Sum
from django.utils import timezone

User = get_user_model()


class UserService:
    """Service class for user-related business logic."""

    EMAIL_VERIFICATION_TIMEOUT = 86400  # 24 hours in seconds
    PASSWORD_RESET_TIMEOUT = 3600  # 1 hour in seconds

    @staticmethod
    @transaction.atomic
    def create_user(email: str, password: str, **extra_fields) -> User:
        """Create a new user with the given credentials."""
        user = User.objects.create_user(
            email=email,
            password=password,
            **extra_fields
        )

        # Generate and send verification email
        UserService._send_verification_email(user)

        return user

    @staticmethod
    def _send_verification_email(user: User) -> None:
        """Generate verification token and send email."""
        # Generate secure token
        token = secrets.token_urlsafe(32)

        # Store token in cache with user ID
        cache_key = f"email_verify:{token}"
        cache.set(cache_key, str(user.id), timeout=UserService.EMAIL_VERIFICATION_TIMEOUT)

        # Also store reverse mapping for token invalidation
        user_cache_key = f"user_verify_token:{user.id}"
        cache.set(user_cache_key, token, timeout=UserService.EMAIL_VERIFICATION_TIMEOUT)

        # Send verification email via Celery
        from celery_tasks.email_tasks import send_verification_email
        send_verification_email.delay(str(user.id), token)

    @staticmethod
    @transaction.atomic
    def verify_email(token: str) -> bool:
        """Verify user's email address with token."""
        cache_key = f"email_verify:{token}"
        user_id = cache.get(cache_key)

        if not user_id:
            return False

        try:
            user = User.objects.get(id=user_id)
        except User.DoesNotExist:
            return False

        if user.is_verified:
            # Already verified, clean up token
            cache.delete(cache_key)
            return True

        user.is_verified = True
        user.save(update_fields=['is_verified'])

        # Clean up tokens
        cache.delete(cache_key)
        cache.delete(f"user_verify_token:{user_id}")

        return True

    @staticmethod
    def resend_verification_email(user: User) -> bool:
        """Resend verification email if not already verified."""
        if user.is_verified:
            return False

        # Invalidate old token
        old_token_key = f"user_verify_token:{user.id}"
        old_token = cache.get(old_token_key)
        if old_token:
            cache.delete(f"email_verify:{old_token}")
            cache.delete(old_token_key)

        # Send new verification email
        UserService._send_verification_email(user)
        return True

    @staticmethod
    def initiate_password_reset(email: str) -> bool:
        """Initiate password reset process."""
        try:
            user = User.objects.get(email__iexact=email)
        except User.DoesNotExist:
            # Return True even if user doesn't exist (security)
            return True

        # Generate secure token
        token = secrets.token_urlsafe(32)

        # Store token in cache
        cache_key = f"password_reset:{token}"
        cache.set(cache_key, str(user.id), timeout=UserService.PASSWORD_RESET_TIMEOUT)

        # Send password reset email via Celery
        from celery_tasks.email_tasks import send_password_reset_email
        send_password_reset_email.delay(str(user.id), token)

        return True

    @staticmethod
    @transaction.atomic
    def reset_password(token: str, new_password: str) -> bool:
        """Reset password using token."""
        cache_key = f"password_reset:{token}"
        user_id = cache.get(cache_key)

        if not user_id:
            return False

        try:
            user = User.objects.get(id=user_id)
        except User.DoesNotExist:
            return False

        user.set_password(new_password)
        user.save(update_fields=['password'])

        # Clean up token
        cache.delete(cache_key)

        return True

    @staticmethod
    @transaction.atomic
    def upgrade_to_vendor(user: User, vendor_data: dict) -> 'Vendor':
        """Upgrade a regular user to vendor status."""
        from apps.vendors.models import Vendor

        if user.is_vendor:
            raise ValueError('User is already a vendor.')

        # Create vendor profile
        vendor = Vendor.objects.create(
            user=user,
            business_name=vendor_data.get('business_name'),
            business_email=vendor_data.get('business_email', user.email),
            business_phone=vendor_data.get('business_phone', ''),
            business_address=vendor_data.get('business_address', {}),
            description=vendor_data.get('description', ''),
            logo=vendor_data.get('logo'),
            status=Vendor.Status.PENDING,
        )

        user.is_vendor = True
        user.save(update_fields=['is_vendor'])

        return vendor

    @staticmethod
    def get_user_stats(user: User) -> dict:
        """Get user statistics for dashboard."""
        from apps.checkout.models import Order

        orders = Order.objects.filter(user=user)

        total_stats = orders.aggregate(
            total_orders=Count('id'),
            total_spent=Sum('total')
        )

        status_counts = orders.values('status').annotate(count=Count('id'))

        status_map = {item['status']: item['count'] for item in status_counts}

        return {
            'total_orders': total_stats['total_orders'] or 0,
            'total_spent': float(total_stats['total_spent'] or 0),
            'pending_orders': status_map.get(Order.Status.PENDING, 0) + status_map.get(Order.Status.CONFIRMED, 0),
            'processing_orders': status_map.get(Order.Status.PROCESSING, 0),
            'shipped_orders': status_map.get(Order.Status.SHIPPED, 0),
            'delivered_orders': status_map.get(Order.Status.DELIVERED, 0),
            'cancelled_orders': status_map.get(Order.Status.CANCELLED, 0),
        }

    @staticmethod
    def get_user_orders(user: User, status: str = None, page: int = 1, page_size: int = 10) -> dict:
        """Get paginated user orders."""
        from apps.checkout.models import Order

        queryset = Order.objects.filter(user=user).select_related('user').prefetch_related('items')

        if status:
            queryset = queryset.filter(status=status)

        queryset = queryset.order_by('-created_at')

        total_count = queryset.count()
        offset = (page - 1) * page_size
        orders = queryset[offset:offset + page_size]

        return {
            'orders': orders,
            'total_count': total_count,
            'page': page,
            'page_size': page_size,
            'total_pages': (total_count + page_size - 1) // page_size
        }

    @staticmethod
    @transaction.atomic
    def update_profile(user: User, data: dict) -> User:
        """Update user profile information."""
        allowed_fields = ['first_name', 'last_name', 'phone']

        for field in allowed_fields:
            if field in data:
                setattr(user, field, data[field])

        user.save(update_fields=[f for f in allowed_fields if f in data])
        return user

    @staticmethod
    @transaction.atomic
    def change_password(user: User, old_password: str, new_password: str) -> bool:
        """Change user password."""
        if not user.check_password(old_password):
            raise ValueError("Current password is incorrect.")

        user.set_password(new_password)
        user.save(update_fields=['password'])
        return True

    @staticmethod
    def get_user_addresses(user: User) -> list:
        """Get all addresses for a user."""
        from apps.users.models import Address

        return list(Address.objects.filter(user=user).values(
            'id', 'address_type', 'is_default', 'street_address',
            'apartment', 'city', 'state', 'postal_code', 'country', 'phone'
        ))

    @staticmethod
    @transaction.atomic
    def add_address(user: User, address_data: dict) -> 'Address':
        """Add a new address for the user."""
        from apps.users.models import Address

        # If this is set as default, unset other defaults of same type
        if address_data.get('is_default'):
            Address.objects.filter(
                user=user,
                address_type=address_data.get('address_type', Address.AddressType.SHIPPING)
            ).update(is_default=False)

        return Address.objects.create(user=user, **address_data)

    @staticmethod
    @transaction.atomic
    def update_address(user: User, address_id: int, address_data: dict) -> 'Address':
        """Update an existing address."""
        from apps.users.models import Address

        try:
            address = Address.objects.get(id=address_id, user=user)
        except Address.DoesNotExist:
            raise ValueError("Address not found.")

        # Handle default flag
        if address_data.get('is_default') and not address.is_default:
            Address.objects.filter(
                user=user,
                address_type=address.address_type
            ).update(is_default=False)

        for key, value in address_data.items():
            if hasattr(address, key):
                setattr(address, key, value)

        address.save()
        return address

    @staticmethod
    @transaction.atomic
    def delete_address(user: User, address_id: int) -> bool:
        """Delete an address."""
        from apps.users.models import Address

        deleted, _ = Address.objects.filter(id=address_id, user=user).delete()
        return deleted > 0

    @staticmethod
    def deactivate_account(user: User, password: str) -> bool:
        """Deactivate user account."""
        if not user.check_password(password):
            raise ValueError("Password is incorrect.")

        user.is_active = False
        user.save(update_fields=['is_active'])
        return True
