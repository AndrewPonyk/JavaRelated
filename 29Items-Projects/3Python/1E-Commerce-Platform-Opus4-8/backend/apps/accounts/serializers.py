"""Account serializers — registration, profile, and token customization."""

from __future__ import annotations

from django.contrib.auth import get_user_model
from django.contrib.auth.password_validation import validate_password
from rest_framework import serializers
from rest_framework_simplejwt.serializers import TokenObtainPairSerializer

User = get_user_model()


class UserSerializer(serializers.ModelSerializer):
    class Meta:
        model = User
        fields = ["id", "email", "username", "role", "first_name", "last_name"]
        read_only_fields = ["id", "role"]


class RegisterSerializer(serializers.ModelSerializer):
    password = serializers.CharField(write_only=True, validators=[validate_password])

    class Meta:
        model = User
        fields = ["id", "email", "username", "password", "role", "first_name", "last_name"]
        read_only_fields = ["id"]
        extra_kwargs = {
            "username": {"required": False},
            "role": {"required": False},
        }

    def validate_email(self, value: str) -> str:
        value = value.lower().strip()
        if User.objects.filter(email=value).exists():
            raise serializers.ValidationError("A user with this email already exists.")
        return value

    def validate_role(self, value: str) -> str:
        # Customers and vendors may self-register; staff cannot.
        if value == User.Role.STAFF:
            raise serializers.ValidationError("Cannot self-register as staff.")
        return value

    def create(self, validated_data: dict) -> User:
        password = validated_data.pop("password")
        validated_data.setdefault("username", validated_data["email"])
        user = User(**validated_data)
        user.set_password(password)
        user.save()
        return user


class EmailTokenObtainPairSerializer(TokenObtainPairSerializer):
    """Adds the user's role + email into the access token claims and response."""

    @classmethod
    def get_token(cls, user):
        token = super().get_token(user)
        token["role"] = user.role
        token["email"] = user.email
        return token

    def validate(self, attrs):
        data = super().validate(attrs)
        data["user"] = UserSerializer(self.user).data
        return data
