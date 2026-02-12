"""
User Model

Custom user model with additional fields for e-commerce.
"""

from django.contrib.auth.models import AbstractUser, BaseUserManager
from django.db import models
from django.utils import timezone


class UserManager(BaseUserManager):
    """Custom user manager for email-based authentication."""

    def create_user(self, email, password=None, **extra_fields):
        """Create and save a regular user with the given email and password."""
        if not email:
            raise ValueError("The Email field must be set")
        email = self.normalize_email(email)
        user = self.model(email=email, **extra_fields)
        user.set_password(password)
        user.save(using=self._db)
        return user

    def create_superuser(self, email, password=None, **extra_fields):
        """Create and save a superuser with the given email and password."""
        extra_fields.setdefault("is_staff", True)
        extra_fields.setdefault("is_superuser", True)
        extra_fields.setdefault("is_active", True)

        if extra_fields.get("is_staff") is not True:
            raise ValueError("Superuser must have is_staff=True.")
        if extra_fields.get("is_superuser") is not True:
            raise ValueError("Superuser must have is_superuser=True.")

        return self.create_user(email, password, **extra_fields)


class User(AbstractUser):
    """
    Custom user model for the e-commerce platform.

    Extends Django's AbstractUser to add e-commerce specific fields.
    """

    class Meta:
        db_table = "users"
        verbose_name = "User"
        verbose_name_plural = "Users"
        indexes = [
            models.Index(fields=["email"]),
            models.Index(fields=["is_vendor"]),
        ]

    # Override username to use email as unique identifier
    username = None  # type: ignore
    email = models.EmailField(unique=True, db_index=True)
    first_name = models.CharField(max_length=150)
    last_name = models.CharField(max_length=150)

    # Additional fields
    phone = models.CharField(max_length=20, blank=True, null=True)
    date_of_birth = models.DateField(blank=True, null=True)
    avatar = models.ImageField(upload_to="avatars/", blank=True, null=True)

    # Vendor status
    is_vendor = models.BooleanField(default=False)
    vendor_profile = models.ForeignKey(
        "Vendor",
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name="users",
    )

    # Verification
    is_verified = models.BooleanField(default=False)
    verification_token = models.CharField(max_length=255, blank=True, null=True)
    verification_token_expires = models.DateTimeField(blank=True, null=True)

    # Password reset
    reset_token = models.CharField(max_length=255, blank=True, null=True)
    reset_token_expires = models.DateTimeField(blank=True, null=True)

    # Timestamps
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)
    last_login_ip = models.GenericIPAddressField(blank=True, null=True)

    # Use email as the USERNAME_FIELD
    USERNAME_FIELD = "email"
    REQUIRED_FIELDS = ["first_name", "last_name"]

    objects = UserManager()

    def __str__(self) -> str:
        return self.email

    @property
    def full_name(self) -> str:
        """Get the user's full name."""
        return f"{self.first_name} {self.last_name}".strip()

    def generate_verification_token(self) -> str:
        """Generate a new email verification token."""
        import secrets

        self.verification_token = secrets.token_urlsafe(32)
        self.verification_token_expires = timezone.now() + timezone.timedelta(hours=24)
        self.save(update_fields=["verification_token", "verification_token_expires"])
        return self.verification_token

    def verify_email(self, token: str) -> bool:
        """Verify the user's email using the provided token."""
        if (
            self.verification_token == token
            and self.verification_token_expires
            and self.verification_token_expires > timezone.now()
        ):
            self.is_verified = True
            self.verification_token = None
            self.verification_token_expires = None
            self.save(update_fields=["is_verified", "verification_token", "verification_token_expires"])
            return True
        return False

    def generate_password_reset_token(self) -> str:
        """Generate a new password reset token."""
        import secrets

        self.reset_token = secrets.token_urlsafe(32)
        self.reset_token_expires = timezone.now() + timezone.timedelta(hours=1)
        self.save(update_fields=["reset_token", "reset_token_expires"])
        return self.reset_token

    def verify_password_reset_token(self, token: str) -> bool:
        """Verify the password reset token is valid and not expired."""
        return (
            self.reset_token == token
            and self.reset_token_expires
            and self.reset_token_expires > timezone.now()
        )

    def reset_password(self, token: str, new_password: str) -> bool:
        """Reset password using the provided token."""
        if self.verify_password_reset_token(token):
            self.set_password(new_password)
            self.reset_token = None
            self.reset_token_expires = None
            self.save(update_fields=["password", "reset_token", "reset_token_expires"])
            return True
        return False


# TODO: Add user preferences model (notifications, theme, etc.)
# TODO: Add user address model (multiple shipping addresses)
