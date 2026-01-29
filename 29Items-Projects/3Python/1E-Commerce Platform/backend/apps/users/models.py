"""User models for E-Commerce Platform."""
from django.contrib.auth.models import AbstractUser, BaseUserManager
from django.db import models


# |su:1 Custom Manager is REQUIRED when changing USERNAME_FIELD
# Django's default UserManager expects 'username', so we must override
# create_user() and create_superuser() to use 'email' instead
class UserManager(BaseUserManager):
    """Custom user manager for email-based authentication."""

    def create_user(self, email, password=None, **extra_fields):
        """Create and save a regular user with the given email and password."""
        if not email:
            raise ValueError('The Email field must be set')
        email = self.normalize_email(email)
        user = self.model(email=email, **extra_fields)
        user.set_password(password)
        user.save(using=self._db)
        return user

    def create_superuser(self, email, password=None, **extra_fields):
        """Create and save a superuser with the given email and password."""
        extra_fields.setdefault('is_staff', True)
        extra_fields.setdefault('is_superuser', True)

        if extra_fields.get('is_staff') is not True:
            raise ValueError('Superuser must have is_staff=True.')
        if extra_fields.get('is_superuser') is not True:
            raise ValueError('Superuser must have is_superuser=True.')

        return self.create_user(email, password, **extra_fields)


# |su:2 AbstractUser vs AbstractBaseUser:
# - AbstractUser: includes username, first_name, last_name, is_staff, etc. (use when you just want to modify)
# - AbstractBaseUser: bare minimum (password, last_login only) - use when building from scratch
class User(AbstractUser):
    """Custom user model with email as the primary identifier."""

    # |su:3 Setting username=None removes the field inherited from AbstractUser
    # This is required when switching to email-based auth
    username = None
    email = models.EmailField('email address', unique=True)

    # Profile fields
    phone_number = models.CharField(max_length=20, blank=True)
    avatar = models.ImageField(upload_to='avatars/', blank=True, null=True)

    # Status fields
    is_verified = models.BooleanField(default=False)
    is_vendor = models.BooleanField(default=False)

    # Timestamps
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    # |su:4 'objects' connects the custom manager to the model
    # Without this, User.objects.create_user() would fail
    objects = UserManager()

    # |su:5 USERNAME_FIELD: the field used for authentication (login)
    # REQUIRED_FIELDS: extra fields prompted during createsuperuser (NOT for regular signup)
    USERNAME_FIELD = 'email'
    REQUIRED_FIELDS = ['first_name', 'last_name']

    class Meta:
        verbose_name = 'user'
        verbose_name_plural = 'users'
        ordering = ['-created_at']

    def __str__(self):
        return self.email

    @property
    def full_name(self):
        """Return the user's full name."""
        return f'{self.first_name} {self.last_name}'.strip()


class Address(models.Model):
    """User shipping/billing address model."""

    # |su:6 TextChoices creates an enum with DB value and display label
    # Usage: AddressType.SHIPPING returns 'shipping', AddressType.SHIPPING.label returns 'Shipping'
    class AddressType(models.TextChoices):
        SHIPPING = 'shipping', 'Shipping'
        BILLING = 'billing', 'Billing'

    # |su:7 on_delete=CASCADE: delete addresses when user is deleted
    # related_name='addresses' allows user.addresses.all() reverse lookup
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='addresses')
    address_type = models.CharField(max_length=10, choices=AddressType.choices)
    is_default = models.BooleanField(default=False)

    # Address fields
    street_address = models.CharField(max_length=255)
    apartment = models.CharField(max_length=100, blank=True)
    city = models.CharField(max_length=100)
    state = models.CharField(max_length=100)
    postal_code = models.CharField(max_length=20)
    country = models.CharField(max_length=100, default='United States')

    # Timestamps
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = 'address'
        verbose_name_plural = 'addresses'
        ordering = ['-is_default', '-created_at']

    def __str__(self):
        return f'{self.street_address}, {self.city}, {self.state}'

    # |su:8 Override save() to enforce business rules before DB write
    # IMPORTANT: always call super().save() at the end or object won't be saved!
    def save(self, *args, **kwargs):
        """Ensure only one default address per type per user."""
        if self.is_default:
            # Reset other defaults before saving this one
            Address.objects.filter(
                user=self.user,
                address_type=self.address_type,
                is_default=True
            ).update(is_default=False)
        super().save(*args, **kwargs)


class WishlistItem(models.Model):
    """User wishlist item model."""

    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='wishlist_items')
    # |su:9 String reference 'products.Product' avoids circular import
    # Use when referencing a model from another app that might import this one
    product = models.ForeignKey(
        'products.Product',
        on_delete=models.CASCADE,
        related_name='wishlisted_by'
    )
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        verbose_name = 'wishlist item'
        verbose_name_plural = 'wishlist items'
        # |su:10 unique_together: composite unique constraint at DB level
        # Prevents duplicate wishlist entries (same user + same product)
        unique_together = ['user', 'product']
        ordering = ['-created_at']

    def __str__(self):
        return f'{self.user.email} - {self.product.name}'
