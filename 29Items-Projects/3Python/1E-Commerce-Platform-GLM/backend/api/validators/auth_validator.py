"""
Auth Validators

Request validators for authentication operations.
"""

from rest_framework import serializers
from django.contrib.auth.password_validation import validate_password


class AuthValidator:
    """Validators for authentication-related requests."""

    class Register(serializers.Serializer):
        """Validator for user registration."""

        email = serializers.EmailField(required=True)
        password = serializers.CharField(required=True, min_length=8, max_length=128)
        first_name = serializers.CharField(max_length=150, required=True)
        last_name = serializers.CharField(max_length=150, required=True)
        phone = serializers.CharField(max_length=20, required=False, allow_blank=True)

        def validate_email(self, value):
            """Check if email is already registered."""
            from django.contrib.auth import get_user_model

            User = get_user_model()
            if User.objects.filter(email=value).exists():
                raise serializers.ValidationError("A user with this email already exists.")
            return value

        def validate_password(self, value):
            """Validate password strength."""
            validate_password(value)
            return value

    class Login(serializers.Serializer):
        """Validator for user login."""

        email = serializers.EmailField(required=True)
        password = serializers.CharField(required=True)

    class reset_password(serializers.Serializer):
        """Validator for password reset."""

        token = serializers.CharField(required=True)
        new_password = serializers.CharField(required=True, min_length=8, max_length=128)
        confirm_password = serializers.CharField(required=True)

        def validate(self, attrs):
            """Validate password reset data."""
            if attrs["new_password"] != attrs["confirm_password"]:
                raise serializers.ValidationError({
                    "confirm_password": "Passwords do not match"
                })

            validate_password(attrs["new_password"])
            return attrs
