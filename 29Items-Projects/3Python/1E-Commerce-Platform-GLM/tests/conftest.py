"""
Pytest Configuration

Shared fixtures and configuration for all tests.
"""

import pytest
import os
import sys
from pathlib import Path

# Add project root to Python path
sys.path.insert(0, str(Path(__file__).parent.parent.parent))

# Set environment variables for tests BEFORE any Django imports
os.environ.setdefault("DJANGO_SECRET_KEY", "test-secret-key-for-pytest-only-do-not-use-in-production")


def pytest_configure(config):
    """Configure pytest with custom settings."""
    config.addinivalue_line("markers", "django_db: Tests that require database access")
    config.addinivalue_line("markers", "unit: Unit tests")
    config.addinivalue_line("markers", "integration: Integration tests")
    config.addinivalue_line("markers", "e2e: End-to-end tests")
    config.addinivalue_line("markers", "slow: Slow running tests")
