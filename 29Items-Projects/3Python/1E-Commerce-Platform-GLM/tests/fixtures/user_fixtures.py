"""
User Fixtures

Test fixtures for creating user-related test data.
"""

import pytest
from django.contrib.auth import get_user_model
from django.utils import timezone
from datetime import datetime, timedelta

User = get_user_model()


@pytest.fixture
def test_user_data():
    """
    Returns a dictionary with valid user data.
    """
    return {
        "email": "test@example.com",
        "password": "SecurePass123!",
        "first_name": "John",
        "last_name": "Doe",
        "phone": "+1234567890",
    }


@pytest.fixture
def test_user(test_user_data):
    """
    Creates and returns a test user instance.
    """
    return User.objects.create_user(**test_user_data)


@pytest.fixture
def test_admin(test_user_data):
    """
    Creates and returns an admin user instance.
    """
    user = User.objects.create_user(**test_user_data)
    user.is_staff = True
    user.is_superuser = True
    user.save()
    return user


@pytest.fixture
def test_vendor(test_user, test_user_data):
    """
    Creates a vendor user.
    """
    from backend.core.models import Vendor

    user = User.objects.create_user(
        email="vendor@example.com",
        password="SecurePass123!",
        first_name="Jane",
        last_name="Vendor",
        is_vendor=True,
    )

    # Create vendor profile
    vendor = Vendor.objects.create(
        name="Test Vendor",
        slug="test-vendor",
        business_name="Test Vendor Inc",
        business_type="company",
        email="vendor@example.com",
        phone="+1234567890",
    )
    user.vendor_profile = vendor
    user.save()

    return user


@pytest.fixture
def multiple_users():
    """
    Creates multiple test users.
    """
    users = []
    for i in range(5):
        user = User.objects.create_user(
            email=f"user{i}@example.com",
            password=f"password{i}!",
            first_name=f"User{i}",
            last_name=f"Test{i}",
        )
        users.append(user)
    return users


@pytest.fixture
def verified_user():
    """
    Creates an email-verified user.
    """
    user = User.objects.create_user(
        email="verified@example.com",
        password="SecurePass123!",
        first_name="Verified",
        last_name="User",
    )
    user.is_verified = True
    user.save()
    return user
