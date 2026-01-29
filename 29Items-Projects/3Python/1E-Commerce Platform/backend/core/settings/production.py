"""Production settings for E-Commerce Platform."""
from .base import *  # noqa: F401, F403

DEBUG = False

# Security settings - stricter than staging
SECURE_BROWSER_XSS_FILTER = True
SECURE_CONTENT_TYPE_NOSNIFF = True
X_FRAME_OPTIONS = 'DENY'
SECURE_HSTS_SECONDS = 31536000  # 1 year
SECURE_HSTS_INCLUDE_SUBDOMAINS = True
SECURE_HSTS_PRELOAD = True

# SSL settings (behind load balancer)
SECURE_PROXY_SSL_HEADER = ('HTTP_X_FORWARDED_PROTO', 'https')
USE_X_FORWARDED_HOST = True
SECURE_SSL_REDIRECT = True

# Session security
SESSION_COOKIE_SECURE = True
SESSION_COOKIE_HTTPONLY = True
SESSION_COOKIE_SAMESITE = 'Strict'
CSRF_COOKIE_SECURE = True
CSRF_COOKIE_SAMESITE = 'Strict'

# AWS S3 Storage for production
AWS_ACCESS_KEY_ID = config('AWS_ACCESS_KEY_ID')  # noqa: F405
AWS_SECRET_ACCESS_KEY = config('AWS_SECRET_ACCESS_KEY')  # noqa: F405
AWS_STORAGE_BUCKET_NAME = config('AWS_STORAGE_BUCKET_NAME', default='ecommerce-prod')  # noqa: F405
AWS_S3_REGION_NAME = config('AWS_S3_REGION_NAME', default='us-east-1')  # noqa: F405
AWS_DEFAULT_ACL = 'private'
AWS_S3_CUSTOM_DOMAIN = f'{AWS_STORAGE_BUCKET_NAME}.s3.amazonaws.com'
AWS_S3_OBJECT_PARAMETERS = {'CacheControl': 'max-age=604800'}  # 1 week

DEFAULT_FILE_STORAGE = 'storages.backends.s3boto3.S3Boto3Storage'

# CloudFront CDN
AWS_CLOUDFRONT_DOMAIN = config('AWS_CLOUDFRONT_DOMAIN', default='')  # noqa: F405
if AWS_CLOUDFRONT_DOMAIN:
    AWS_S3_CUSTOM_DOMAIN = AWS_CLOUDFRONT_DOMAIN

# Database - use read replica for read operations
# TODO: Implement database router for read/write splitting
# DATABASE_ROUTERS = ['core.routers.ReadReplicaRouter']

# Email configuration
EMAIL_BACKEND = 'django.core.mail.backends.smtp.EmailBackend'
EMAIL_HOST = config('EMAIL_HOST')  # noqa: F405
EMAIL_PORT = config('EMAIL_PORT', cast=int)  # noqa: F405
EMAIL_USE_TLS = True
EMAIL_HOST_USER = config('EMAIL_HOST_USER')  # noqa: F405
EMAIL_HOST_PASSWORD = config('EMAIL_HOST_PASSWORD')  # noqa: F405

# Sentry error tracking
SENTRY_DSN = config('SENTRY_DSN')  # noqa: F405
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
        environment='production',
        traces_sample_rate=0.1,  # Lower sampling in production
        send_default_pii=False,
    )

# Production logging - JSON format for log aggregation
LOGGING['formatters']['default'] = {  # noqa: F405
    '()': 'pythonjsonlogger.jsonlogger.JsonFormatter',
    'format': '%(asctime)s %(levelname)s %(name)s %(message)s',
}
LOGGING['handlers']['console']['formatter'] = 'default'  # noqa: F405
LOGGING['root']['level'] = 'WARNING'  # noqa: F405
