"""
Authentication Service

Business logic for user authentication operations.
"""

from typing import Optional, Dict, Any
from datetime import datetime, timedelta
from django.contrib.auth import get_user_model
from django.core.cache import cache
from django.utils import timezone
from rest_framework_simplejwt.tokens import RefreshToken
import secrets

from backend.core.utils.cache import cache_result

User = get_user_model()


class AuthenticationService:
    """Service class for authentication-related business logic."""

    def __init__(self):
        self.cache_prefix = "auth"
        self.token_cache_ttl = 300  # 5 minutes

    def register_user(
        self,
        email: str,
        password: str,
        first_name: str,
        last_name: str,
        phone: Optional[str] = None,
    ) -> Dict[str, Any]:
        """
        Register a new user account.

        Args:
            email: User's email address
            password: User's password
            first_name: User's first name
            last_name: User's last name
            phone: Optional phone number

        Returns:
            Dictionary with user data and tokens

        Raises:
            ValueError: If email already exists or validation fails
        """
        # Check if email already exists
        if User.objects.filter(email=email).exists():
            raise ValueError("A user with this email already exists.")

        # Create user
        user = User.objects.create_user(
            email=email,
            password=password,
            first_name=first_name,
            last_name=last_name,
            phone=phone,
        )

        # Generate JWT tokens
        refresh = RefreshToken.for_user(user)

        return {
            "user": self._serialize_user(user),
            "access_token": str(refresh.access_token),
            "refresh_token": str(refresh),
        }

    def login_user(self, email: str, password: str) -> Dict[str, Any]:
        """
        Authenticate user with email and password.

        Args:
            email: User's email address
            password: User's password

        Returns:
            Dictionary with user data and tokens

        Raises:
            ValueError: If credentials are invalid
        """
        from django.contrib.auth import authenticate

        user = authenticate(username=email, password=password)

        if user is None:
            raise ValueError("Invalid email or password.")

        if not user.is_active:
            raise ValueError("This account has been deactivated.")

        # Update last login
        user.last_login = timezone.now()
        user.save(update_fields=["last_login"])

        # Generate JWT tokens
        refresh = RefreshToken.for_user(user)

        return {
            "user": self._serialize_user(user),
            "access_token": str(refresh.access_token),
            "refresh_token": str(refresh),
        }

    def logout_user(self, user_id: int, refresh_token: str) -> bool:
        """
        Logout user and blacklist refresh token.

        Args:
            user_id: The user ID
            refresh_token: The refresh token to blacklist

        Returns:
            True if logout successful
        """
        try:
            # Blacklist the refresh token
            from rest_framework_simplejwt.tokens import RefreshToken as Token

            token = Token(refresh_token)
            token.blacklist()

            # Clear user sessions from cache
            cache.delete_many([
                f"{self.cache_prefix}:session:{user_id}",
                f"{self.cache_prefix}:permissions:{user_id}",
            ])

            return True

        except Exception:
            return False

    def refresh_tokens(self, refresh_token: str) -> Dict[str, str]:
        """
        Refresh access token using refresh token.

        Args:
            refresh_token: The refresh token

        Returns:
            Dictionary with new access token

        Raises:
            ValueError: If refresh token is invalid
        """
        try:
            from rest_framework_simplejwt.tokens import RefreshToken as Token

            refresh = Token(refresh_token)

            return {
                "access_token": str(refresh.access_token),
            }

        except Exception:
            raise ValueError("Invalid or expired refresh token.")

    def get_user_by_id(self, user_id: int) -> Optional[Dict[str, Any]]:
        """
        Get user by ID with caching.

        Args:
            user_id: The user ID

        Returns:
            User data dictionary or None
        """
        cache_key = f"{self.cache_prefix}:user:{user_id}"
        user_data = cache.get(cache_key)

        if user_data is None:
            try:
                user = User.objects.get(id=user_id)
                user_data = self._serialize_user(user)
                cache.set(cache_key, user_data, self.token_cache_ttl)
            except User.DoesNotExist:
                return None

        return user_data

    def update_user_profile(
        self,
        user_id: int,
        first_name: Optional[str] = None,
        last_name: Optional[str] = None,
        phone: Optional[str] = None,
        date_of_birth: Optional[str] = None,
        avatar: Optional[str] = None,
    ) -> Dict[str, Any]:
        """
        Update user profile information.

        Args:
            user_id: The user ID
            first_name: New first name
            last_name: New last name
            phone: New phone number
            date_of_birth: New date of birth (ISO format)
            avatar: New avatar URL

        Returns:
            Updated user data dictionary

        Raises:
            ValueError: If user doesn't exist
        """
        try:
            user = User.objects.get(id=user_id)

            if first_name is not None:
                user.first_name = first_name
            if last_name is not None:
                user.last_name = last_name
            if phone is not None:
                user.phone = phone
            if date_of_birth is not None:
                from datetime import datetime

                user.date_of_birth = datetime.fromisoformat(date_of_birth).date()
            if avatar is not None:
                user.avatar = avatar

            user.save()

            # Invalidate cache
            cache.delete(f"{self.cache_prefix}:user:{user_id}")

            return self._serialize_user(user)

        except User.DoesNotExist:
            raise ValueError("User not found.")

    def change_password(
        self,
        user_id: int,
        old_password: str,
        new_password: str,
    ) -> bool:
        """
        Change user password.

        Args:
            user_id: The user ID
            old_password: Current password
            new_password: New password

        Returns:
            True if password changed successfully

        Raises:
            ValueError: If old password is incorrect
        """
        try:
            user = User.objects.get(id=user_id)

            if not user.check_password(old_password):
                raise ValueError("Current password is incorrect.")

            user.set_password(new_password)
            user.save()

            # Invalidate all user sessions
            cache.delete_many([
                f"{self.cache_prefix}:session:{user_id}",
                f"{self.cache_prefix}:permissions:{user_id}",
            ])

            return True

        except User.DoesNotExist:
            raise ValueError("User not found.")

    def request_password_reset(self, email: str) -> str:
        """
        Request password reset and generate reset token.

        Args:
            email: User's email address

        Returns:
            Password reset token

        Raises:
            ValueError: If email not found
        """
        try:
            user = User.objects.get(email=email)
        except User.DoesNotExist:
            # Don't reveal if email exists
            return ""

        # Generate reset token
        reset_token = secrets.token_urlsafe(32)
        reset_token_expires = timezone.now() + timedelta(hours=24)

        # Store token in cache
        cache_key = f"{self.cache_prefix}:reset:{reset_token}"
        cache.set(
            cache_key,
            {"user_id": user.id, "expires": reset_token_expires.isoformat()},
            timeout=86400,  # 24 hours
        )

        return reset_token

    def reset_password(self, token: str, new_password: str) -> bool:
        """
        Reset password using reset token.

        Args:
            token: Password reset token
            new_password: New password

        Returns:
            True if password reset successful

        Raises:
            ValueError: If token is invalid or expired
        """
        cache_key = f"{self.cache_prefix}:reset:{token}"
        token_data = cache.get(cache_key)

        if token_data is None:
            raise ValueError("Invalid or expired reset token.")

        # Check if token is expired
        from datetime import datetime

        expires = datetime.fromisoformat(token_data["expires"])
        if timezone.now() > expires:
            cache.delete(cache_key)
            raise ValueError("Reset token has expired.")

        # Update user password
        try:
            user = User.objects.get(id=token_data["user_id"])
            user.set_password(new_password)
            user.save()

            # Delete the token
            cache.delete(cache_key)

            # Invalidate user sessions
            cache.delete_many([
                f"{self.cache_prefix}:session:{user_id}",
                f"{self.cache_prefix}:permissions:{user_id}",
            ])

            return True

        except User.DoesNotExist:
            raise ValueError("User not found.")

    def verify_email(self, user_id: int, token: str) -> bool:
        """
        Verify user email with verification token.

        Args:
            user_id: The user ID
            token: Verification token

        Returns:
            True if email verified successfully
        """
        try:
            user = User.objects.get(id=user_id)
            return user.verify_email(token)
        except User.DoesNotExist:
            return False

    def get_user_permissions(self, user_id: int) -> list[str]:
        """
        Get list of user permissions/roles.

        Args:
            user_id: The user ID

        Returns:
            List of permission strings
        """
        cache_key = f"{self.cache_prefix}:permissions:{user_id}"
        permissions = cache.get(cache_key)

        if permissions is None:
            try:
                user = User.objects.get(id=user_id)
                permissions = []

                # Add basic permissions
                if user.is_authenticated:
                    permissions.append("authenticated")

                # Add vendor permissions
                if user.is_vendor:
                    permissions.extend([
                        "vendor.view_dashboard",
                        "vendor.manage_products",
                        "vendor.view_orders",
                    ])

                # Add staff permissions
                if user.is_staff:
                    permissions.extend([
                        "admin.view_all",
                        "admin.manage_users",
                        "admin.manage_products",
                    ])

                # Add superuser permissions
                if user.is_superuser:
                    permissions.append("superuser")

                cache.set(cache_key, permissions, 3600)  # 1 hour

            except User.DoesNotExist:
                return []

        return permissions or []

    def _serialize_user(self, user: User) -> Dict[str, Any]:
        """Serialize user object to dictionary."""
        return {
            "id": user.id,
            "email": user.email,
            "first_name": user.first_name,
            "last_name": user.last_name,
            "full_name": user.full_name,
            "phone": user.phone,
            "avatar": user.avatar.url if user.avatar else None,
            "is_vendor": user.is_vendor,
            "is_verified": user.is_verified,
            "is_active": user.is_active,
            "is_staff": user.is_staff,
            "date_joined": user.date_joined.isoformat() if hasattr(user, "date_joined") else None,
            "created_at": user.created_at.isoformat(),
        }
