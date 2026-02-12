"""
Core Utilities Package

Common utility functions and helpers.
"""

from backend.core.utils.cache import (
    cache_result,
    invalidate_cache_pattern,
    get_cached_or_compute,
    generate_hash_key,
    CacheLock,
)
from backend.core.utils.db import (
    bulk_create_or_update,
    get_or_none,
    chunked_queryset,
    safe_bulk_create,
    update_with_retry,
)
from backend.core.utils.logger import (
    setup_logging,
    get_logger,
    LogContext,
    log_exception,
)

__all__ = [
    # Cache utilities
    "cache_result",
    "invalidate_cache_pattern",
    "get_cached_or_compute",
    "generate_hash_key",
    "CacheLock",
    # Database utilities
    "bulk_create_or_update",
    "get_or_none",
    "chunked_queryset",
    "safe_bulk_create",
    "update_with_retry",
    # Logging utilities
    "setup_logging",
    "get_logger",
    "LogContext",
    "log_exception",
]
