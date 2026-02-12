"""
Cache Utilities

Helper functions for cache operations using Redis.
"""

from functools import wraps
from typing import Any, Callable, Optional
from django.core.cache import cache
from django.conf import settings
import hashlib
import json
import logging

logger = logging.getLogger("backend")


def cache_result(
    timeout: int = 300,
    key_prefix: str = "",
    key_func: Optional[Callable] = None,
):
    """
    Decorator to cache function results.

    Args:
        timeout: Cache timeout in seconds
        key_prefix: Prefix for cache key
        key_func: Custom function to generate cache key

    Example:
        @cache_result(timeout=600, key_prefix="product")
        def get_product(product_id):
            return Product.objects.get(id=product_id)
    """

    def decorator(func: Callable) -> Callable:
        @wraps(func)
        def wrapper(*args, **kwargs) -> Any:
            # Generate cache key
            if key_func:
                cache_key = key_func(*args, **kwargs)
            else:
                # Generate default key from function name and arguments
                key_parts = [key_prefix, func.__name__]
                if args:
                    key_parts.extend(str(arg) for arg in args)
                if kwargs:
                    key_parts.extend(f"{k}={v}" for k, v in sorted(kwargs.items()))
                cache_key = ":".join(key_parts)

            # Try to get from cache
            cached_value = cache.get(cache_key)
            if cached_value is not None:
                return cached_value

            # Execute function and cache result
            result = func(*args, **kwargs)
            cache.set(cache_key, result, timeout)

            return result

        return wrapper

    return decorator


def invalidate_cache_pattern(pattern: str) -> int:
    """
    Invalidate all cache keys matching a pattern.

    Args:
        pattern: Cache key pattern (supports * wildcard)

    Returns:
        Number of keys invalidated
    """
    # Note: Django's default cache backend doesn't support pattern deletion
    # This is a simplified version that works with Redis
    try:
        from django.core.cache import cache
        if hasattr(cache, "delete_pattern"):
            return cache.delete_pattern(pattern)
        else:
            # Fallback: log warning
            logger.warning(f"Cache backend doesn't support pattern deletion: {pattern}")
            return 0
    except Exception as e:
        logger.error(f"Failed to invalidate cache pattern {pattern}: {e}")
        return 0


def get_cached_or_compute(
    cache_key: str,
    compute_func: Callable,
    timeout: int = 300,
) -> Any:
    """
    Get value from cache or compute and cache it.

    Args:
        cache_key: The cache key
        compute_func: Function to compute value if not cached
        timeout: Cache timeout in seconds

    Returns:
        Cached or computed value
    """
    value = cache.get(cache_key)

    if value is None:
        value = compute_func()
        cache.set(cache_key, value, timeout)

    return value


def generate_hash_key(*args, **kwargs) -> str:
    """
    Generate a hash-based cache key from arguments.

    Args:
        *args: Positional arguments
        **kwargs: Keyword arguments

    Returns:
        MD5 hash of the arguments
    """
    # Serialize arguments to JSON
    key_data = json.dumps({"args": args, "kwargs": kwargs}, sort_keys=True)

    # Generate hash
    return hashlib.md5(key_data.encode()).hexdigest()


class CacheLock:
    """
    Simple distributed lock using cache.
    """

    def __init__(self, lock_key: str, timeout: int = 60):
        """
        Initialize cache lock.

        Args:
            lock_key: The key for the lock
            timeout: Lock timeout in seconds
        """
        self.lock_key = f"lock:{lock_key}"
        self.timeout = timeout
        self.acquired = False

    def __enter__(self):
        """Acquire the lock."""
        self.acquired = cache.add(self.lock_key, "1", self.timeout)
        if not self.acquired:
            raise RuntimeError(f"Could not acquire lock: {self.lock_key}")
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        """Release the lock."""
        if self.acquired:
            cache.delete(self.lock_key)
