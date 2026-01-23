"""
Unit Tests for Validators
"""
import pytest
from app.utils.validators import (
    validate_email,
    validate_phone,
    sanitize_string,
    validate_status,
    validate_pagination
)


class TestEmailValidation:
    """Test cases for email validation."""

    @pytest.mark.parametrize("email,expected", [
        ("valid@email.com", True),
        ("test.name@example.org", True),
        ("user+tag@domain.co.uk", True),
        ("simple@test.io", True),
        ("invalid-email", False),
        ("@nodomain.com", False),
        ("noat.domain.com", False),
        ("", False),
        (None, False),
        ("spaces in@email.com", False),
    ])
    def test_email_validation(self, email, expected):
        """Test various email formats."""
        assert validate_email(email) == expected


class TestPhoneValidation:
    """Test cases for phone validation."""

    @pytest.mark.parametrize("phone,expected", [
        ("+1234567890", True),
        ("123-456-7890", True),
        ("(123) 456-7890", True),
        ("1234567890", True),
        ("+44 20 7946 0958", True),
        ("123.456.7890", True),
        ("", True),  # Empty is valid (phone is optional)
        (None, True),  # None is valid (phone is optional)
        ("123", False),  # Too short
        ("abc-def-ghij", False),  # Not numbers
        ("123456789012345678", False),  # Too long
    ])
    def test_phone_validation(self, phone, expected):
        """Test various phone formats."""
        assert validate_phone(phone) == expected


class TestStringSanitization:
    """Test cases for string sanitization."""

    def test_sanitize_strips_whitespace(self):
        """Test that whitespace is stripped."""
        assert sanitize_string("  hello  ") == "hello"

    def test_sanitize_truncates_long_string(self):
        """Test that long strings are truncated."""
        long_string = "a" * 300
        result = sanitize_string(long_string, max_length=255)
        assert len(result) == 255

    def test_sanitize_empty_string_returns_none(self):
        """Test that empty string returns None."""
        assert sanitize_string("") is None
        assert sanitize_string("   ") is None

    def test_sanitize_none_returns_none(self):
        """Test that None returns None."""
        assert sanitize_string(None) is None

    def test_sanitize_normal_string(self):
        """Test that normal string is returned as-is."""
        assert sanitize_string("hello world") == "hello world"

    def test_sanitize_custom_max_length(self):
        """Test custom max length."""
        result = sanitize_string("hello world", max_length=5)
        assert result == "hello"


class TestStatusValidation:
    """Test cases for status validation."""

    @pytest.mark.parametrize("status,expected", [
        ("active", True),
        ("inactive", True),
        ("lead", True),
        (None, True),  # None is valid (uses default)
        ("invalid", False),
        ("ACTIVE", False),  # Case sensitive
        ("", False),
    ])
    def test_status_validation(self, status, expected):
        """Test various status values."""
        assert validate_status(status) == expected


class TestPaginationValidation:
    """Test cases for pagination validation."""

    def test_valid_pagination(self):
        """Test valid pagination parameters."""
        page, per_page = validate_pagination(1, 20)
        assert page == 1
        assert per_page == 20

    def test_negative_page_normalized(self):
        """Test that negative page is normalized to 1."""
        page, per_page = validate_pagination(-1, 20)
        assert page == 1

    def test_zero_page_normalized(self):
        """Test that zero page is normalized to 1."""
        page, per_page = validate_pagination(0, 20)
        assert page == 1

    def test_per_page_capped_at_max(self):
        """Test that per_page is capped at max."""
        page, per_page = validate_pagination(1, 1000, max_per_page=100)
        assert per_page == 100

    def test_negative_per_page_normalized(self):
        """Test that negative per_page is normalized to 1."""
        page, per_page = validate_pagination(1, -5)
        assert per_page == 1

    def test_custom_max_per_page(self):
        """Test custom max_per_page."""
        page, per_page = validate_pagination(1, 50, max_per_page=25)
        assert per_page == 25
