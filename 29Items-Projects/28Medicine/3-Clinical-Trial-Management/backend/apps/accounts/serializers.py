from __future__ import annotations

from django.contrib.auth.password_validation import validate_password
from rest_framework import serializers

from .models import ElectronicSignature, Role, User


class UserSerializer(serializers.ModelSerializer):
    class Meta:
        model = User
        fields = (
            "id",
            "username",
            "email",
            "first_name",
            "last_name",
            "role",
            "mfa_enrolled",
            "is_read_only",
            "is_active",
        )
        read_only_fields = ("id", "is_read_only", "is_active")


class UserCreateSerializer(serializers.ModelSerializer):
    password = serializers.CharField(write_only=True, validators=[validate_password])

    class Meta:
        model = User
        fields = ("id", "username", "email", "first_name", "last_name", "role", "password")

    def validate_role(self, value: str) -> str:
        if value not in Role.values:
            raise serializers.ValidationError("Unknown role.")
        return value

    def create(self, validated_data: dict) -> User:
        password = validated_data.pop("password")
        user = User(**validated_data)
        user.set_password(password)
        user.save()
        return user


class ElectronicSignatureSerializer(serializers.ModelSerializer):
    signer = serializers.StringRelatedField(read_only=True)

    class Meta:
        model = ElectronicSignature
        fields = ("id", "signer", "meaning", "entity_type", "entity_id", "signed_at")
        read_only_fields = fields
