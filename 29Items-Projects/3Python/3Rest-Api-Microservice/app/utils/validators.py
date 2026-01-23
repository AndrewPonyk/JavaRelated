"""
Input Validation Utilities
"""
import re
from typing import Optional


def validate_email(email: Optional[str]) -> bool:
    """
    Validate email format.

    Args:
        email: Email string to validate

    Returns:
        True if valid, False otherwise
    """
    if not email:
        return False

    # Simple email regex pattern
    pattern = r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$'
    return bool(re.match(pattern, email))


def validate_phone(phone: Optional[str]) -> bool:
    """
    Validate phone number format.

    Accepts various formats:
    - +1234567890
    - 123-456-7890
    - (123) 456-7890
    - 1234567890

    Args:
        phone: Phone string to validate

    Returns:
        True if valid, False otherwise
    """
    if not phone:
        return True  # Phone is optional

    # Remove common formatting characters
    cleaned = re.sub(r'[\s\-\(\)\.]', '', phone)

    # Check if remaining characters are valid (digits and optional leading +)
    pattern = r'^\+?\d{7,15}$'
    return bool(re.match(pattern, cleaned))


def sanitize_string(value: Optional[str], max_length: int = 255) -> Optional[str]:
    """
    Sanitize string input.

    - Strips whitespace
    - Truncates to max length
    - Returns None for empty strings

    Args:
        value: String to sanitize
        max_length: Maximum allowed length

    Returns:
        Sanitized string or None
    """
    if not value:
        return None

    sanitized = value.strip()

    if not sanitized:
        return None

    return sanitized[:max_length]


def validate_status(status: Optional[str]) -> bool:
    """
    Validate customer status value.

    Args:
        status: Status string to validate

    Returns:
        True if valid, False otherwise
    """
    valid_statuses = {'active', 'inactive', 'lead'}
    return status is None or status in valid_statuses


def validate_pagination(page: int, per_page: int, max_per_page: int = 100) -> tuple:
    """
    Validate and normalize pagination parameters.

    Args:
        page: Page number
        per_page: Items per page
        max_per_page: Maximum allowed per_page value

    Returns:
        Tuple of (normalized_page, normalized_per_page)
    """
    normalized_page = max(1, page)
    normalized_per_page = min(max(1, per_page), max_per_page)
    return normalized_page, normalized_per_page
