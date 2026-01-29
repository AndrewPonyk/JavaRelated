"""Development settings for E-Commerce Platform."""
from .base import *  # noqa: F401, F403

DEBUG = True

ALLOWED_HOSTS = ['localhost', '127.0.0.1', '0.0.0.0', 'backend']

# Additional development apps
INSTALLED_APPS += [  # noqa: F405
    'debug_toolbar',
    'django_extensions',
]

# Debug toolbar middleware
MIDDLEWARE.insert(0, 'debug_toolbar.middleware.DebugToolbarMiddleware')  # noqa: F405

INTERNAL_IPS = [
    '127.0.0.1',
    '172.0.0.0/8',  # Docker networks
]

# Docker network compatibility for debug toolbar
import socket
hostname, _, ips = socket.gethostbyname_ex(socket.gethostname())
INTERNAL_IPS += [".".join(ip.split(".")[:-1] + ["1"]) for ip in ips]

# Email backend for development
EMAIL_BACKEND = 'django.core.mail.backends.console.EmailBackend'

# Use Redis cache in Docker, local memory otherwise
import os
if os.environ.get('REDIS_URL'):
    CACHES = {
        'default': {
            'BACKEND': 'django.core.cache.backends.redis.RedisCache',
            'LOCATION': os.environ.get('REDIS_URL'),
            'KEY_PREFIX': 'ecommerce_dev',
        }
    }
else:
    CACHES = {
        'default': {
            'BACKEND': 'django.core.cache.backends.locmem.LocMemCache',
        }
    }

# More verbose logging
LOGGING['loggers']['apps']['level'] = 'DEBUG'  # noqa: F405

# Disable throttling in development
REST_FRAMEWORK['DEFAULT_THROTTLE_CLASSES'] = ()  # noqa: F405
REST_FRAMEWORK['DEFAULT_THROTTLE_RATES'] = {}  # noqa: F405

# Allow all CORS origins in development
CORS_ALLOW_ALL_ORIGINS = True

# Stripe keys
STRIPE_SECRET_KEY = config('STRIPE_SECRET_KEY', default='sk_test_xxx')  # noqa: F405
STRIPE_PUBLIC_KEY = config('STRIPE_PUBLIC_KEY', default='pk_test_xxx')  # noqa: F405
STRIPE_WEBHOOK_SECRET = config('STRIPE_WEBHOOK_SECRET', default='whsec_xxx')  # noqa: F405
