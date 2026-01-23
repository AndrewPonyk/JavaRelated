"""
Custom Decorators
"""
from functools import wraps
from typing import Callable, Optional, List
from flask import request, current_app
from app.utils.exceptions import AuthenticationError, RateLimitError

# Simple in-memory rate limit store (use Redis in production)
_rate_limit_store: dict = {}


def require_api_key(f: Callable) -> Callable:
    """
    Decorator to require API key authentication.

    Checks for X-API-Key header and validates against configured key.

    Usage:
        @app.route('/protected')
        @require_api_key
        def protected_endpoint():
            return {'data': 'secret'}
    """
    @wraps(f)
    def decorated(*args, **kwargs):
        api_key = request.headers.get('X-API-Key')

        if not api_key:
            raise AuthenticationError('API key required')

        expected_key = current_app.config.get('API_KEY')

        if not expected_key:
            # If no API key configured, allow all requests (dev mode)
            return f(*args, **kwargs)

        if api_key != expected_key:
            raise AuthenticationError('Invalid API key')

        return f(*args, **kwargs)
    return decorated


def rate_limit(
    limit: int = 100,
    period: int = 3600,
    key_func: Optional[Callable] = None
) -> Callable:
    """
    Decorator to apply rate limiting.

    Args:
        limit: Maximum requests allowed
        period: Time period in seconds
        key_func: Function to generate rate limit key (default: IP address)

    Usage:
        @app.route('/api/resource')
        @rate_limit(limit=10, period=60)  # 10 requests per minute
        def rate_limited_endpoint():
            return {'data': 'value'}
    """
    def decorator(f: Callable) -> Callable:
        @wraps(f)
        def decorated(*args, **kwargs):
            # TODO: Implement proper rate limiting with Redis
            # This is a simplified in-memory implementation

            if key_func:
                key = key_func()
            else:
                key = request.remote_addr

            rate_key = f'{f.__name__}:{key}'

            # Simple implementation - production should use Redis
            import time
            current_time = time.time()

            if rate_key not in _rate_limit_store:
                _rate_limit_store[rate_key] = {
                    'count': 0,
                    'reset_time': current_time + period
                }

            store = _rate_limit_store[rate_key]

            # Reset if period has passed
            if current_time > store['reset_time']:
                store['count'] = 0
                store['reset_time'] = current_time + period

            store['count'] += 1

            if store['count'] > limit:
                raise RateLimitError(
                    f'Rate limit exceeded. Limit: {limit} requests per {period} seconds'
                )

            return f(*args, **kwargs)
        return decorated
    return decorator


def validate_json(required_fields: Optional[List[str]] = None) -> Callable:
    """
    Decorator to validate JSON request body.

    Args:
        required_fields: List of required field names

    Usage:
        @app.route('/api/resource', methods=['POST'])
        @validate_json(['name', 'email'])
        def create_resource():
            data = request.get_json()
            return {'data': data}
    """
    def decorator(f: Callable) -> Callable:
        @wraps(f)
        def decorated(*args, **kwargs):
            from app.utils.exceptions import ValidationError

            if not request.is_json:
                raise ValidationError('Content-Type must be application/json')

            data = request.get_json()

            if data is None:
                raise ValidationError('Request body must be valid JSON')

            if required_fields:
                missing = [field for field in required_fields if field not in data]
                if missing:
                    raise ValidationError(
                        f'Missing required fields: {", ".join(missing)}'
                    )

            return f(*args, **kwargs)
        return decorated
    return decorator
