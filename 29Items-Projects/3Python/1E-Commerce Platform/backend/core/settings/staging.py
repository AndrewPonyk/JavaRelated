"""Staging settings for E-Commerce Platform."""
from .base import *  # noqa: F401, F403

DEBUG = False

# Security settings
SECURE_BROWSER_XSS_FILTER = True
SECURE_CONTENT_TYPE_NOSNIFF = True
X_FRAME_OPTIONS = 'DENY'
SECURE_HSTS_SECONDS = 3600
SECURE_HSTS_INCLUDE_SUBDOMAINS = True

# SSL settings (behind load balancer)
SECURE_PROXY_SSL_HEADER = ('HTTP_X_FORWARDED_PROTO', 'https')
USE_X_FORWARDED_HOST = True

# Session security
SESSION_COOKIE_SECURE = True
SESSION_COOKIE_HTTPONLY = True
CSRF_COOKIE_SECURE = True

# AWS S3 Storage for staging
AWS_ACCESS_KEY_ID = config('AWS_ACCESS_KEY_ID', default='')  # noqa: F405
AWS_SECRET_ACCESS_KEY = config('AWS_SECRET_ACCESS_KEY', default='')  # noqa: F405
AWS_STORAGE_BUCKET_NAME = config('AWS_STORAGE_BUCKET_NAME', default='ecommerce-staging')  # noqa: F405
AWS_S3_REGION_NAME = config('AWS_S3_REGION_NAME', default='us-east-1')  # noqa: F405
AWS_DEFAULT_ACL = 'private'
AWS_S3_CUSTOM_DOMAIN = f'{AWS_STORAGE_BUCKET_NAME}.s3.amazonaws.com'
AWS_S3_OBJECT_PARAMETERS = {'CacheControl': 'max-age=86400'}

DEFAULT_FILE_STORAGE = 'storages.backends.s3boto3.S3Boto3Storage'

# Email configuration
EMAIL_BACKEND = 'django.core.mail.backends.smtp.EmailBackend'
EMAIL_HOST = config('EMAIL_HOST', default='smtp.sendgrid.net')  # noqa: F405
EMAIL_PORT = config('EMAIL_PORT', default=587, cast=int)  # noqa: F405
EMAIL_USE_TLS = True
EMAIL_HOST_USER = config('EMAIL_HOST_USER', default='')  # noqa: F405
EMAIL_HOST_PASSWORD = config('EMAIL_HOST_PASSWORD', default='')  # noqa: F405

# Sentry error tracking
SENTRY_DSN = config('SENTRY_DSN', default='')  # noqa: F405
if SENTRY_DSN:
    import sentry_sdk
    from sentry_sdk.integrations.django import DjangoIntegration
    from sentry_sdk.integrations.celery import CeleryIntegration
    from sentry_sdk.integrations.redis import RedisIntegration

    sentry_sdk.init(
        dsn=SENTRY_DSN,
        integrations=[
            DjangoIntegration(),
            CeleryIntegration(),
            RedisIntegration(),
        ],
        environment='staging',
        traces_sample_rate=0.5,
        send_default_pii=False,
    )
