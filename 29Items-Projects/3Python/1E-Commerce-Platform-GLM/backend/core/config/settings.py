"""
Django Settings Configuration

Centralized configuration management using environment variables.
"""

import os
from datetime import timedelta
from pathlib import Path
from typing import List, Tuple
from urllib.parse import urlparse

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """
    Application settings loaded from environment variables.
    """

    # =============================================================================
    # Application
    # =============================================================================
    app_name: str = Field(default="ecommerce-platform", alias="APP_NAME")
    app_env: str = Field(default="development", alias="APP_ENV")
    debug: bool = Field(default=True, alias="DEBUG")
    allowed_hosts: List[str] = Field(
        default=["localhost", "127.0.0.1"]
    )
    cors_origins: List[str] = Field(
        default=["http://localhost:3000"]
    )

    # =============================================================================
    # Django Settings
    # =============================================================================
    secret_key: str = Field(..., alias="DJANGO_SECRET_KEY")
    base_dir: Path = Field(default=Path(__file__).parent.parent.parent.parent)
    use_tz: bool = True
    timezone: str = "UTC"

    # =============================================================================
    # Database
    # =============================================================================
    database_url: str = Field(
        default="sqlite:///db.sqlite3" if os.environ.get("PYTEST_CURRENT_TEST") else "postgresql://postgres:postgres@localhost:5432/ecommerce",
        alias="DATABASE_URL",
    )
    database_pool_size: int = Field(default=20, alias="DATABASE_POOL_SIZE")
    database_max_overflow: int = Field(default=10, alias="DATABASE_MAX_OVERFLOW")

    # =============================================================================
    # Redis (Cache & Sessions)
    # =============================================================================
    redis_url: str = Field(default="redis://localhost:6379/0", alias="REDIS_URL")
    cache_ttl: int = Field(default=300, alias="REDIS_CACHE_TTL")
    session_ttl: int = Field(default=86400, alias="REDIS_SESSION_TTL")

    # =============================================================================
    # Elasticsearch
    # =============================================================================
    elasticsearch_url: str = Field(
        default="http://localhost:9200", alias="ELASTICSEARCH_URL"
    )
    elasticsearch_index_prefix: str = Field(
        default="ecommerce", alias="ELASTICSEARCH_INDEX_PREFIX"
    )

    # =============================================================================
    # Celery
    # =============================================================================
    celery_broker_url: str = Field(
        default="redis://localhost:6379/1", alias="CELERY_BROKER_URL"
    )
    celery_result_backend: str = Field(
        default="redis://localhost:6379/2", alias="CELERY_RESULT_BACKEND"
    )
    celery_task_timeout: int = Field(default=300, alias="CELERY_TASK_TIMEOUT")

    # =============================================================================
    # JWT Authentication
    # =============================================================================
    jwt_access_token_expire_minutes: int = Field(
        default=30, alias="JWT_ACCESS_TOKEN_EXPIRE_MINUTES"
    )
    jwt_refresh_token_expire_days: int = Field(
        default=7, alias="JWT_REFRESH_TOKEN_EXPIRE_DAYS"
    )
    jwt_algorithm: str = "HS256"

    # =============================================================================
    # External Services
    # =============================================================================
    # Stripe
    stripe_public_key: str = Field(default="", alias="STRIPE_PUBLIC_KEY")
    stripe_secret_key: str = Field(default="", alias="STRIPE_SECRET_KEY")
    stripe_webhook_secret: str = Field(default="", alias="STRIPE_WEBHOOK_SECRET")

    # SendGrid
    sendgrid_api_key: str = Field(default="", alias="SENDGRID_API_KEY")
    sendgrid_from_email: str = Field(
        default="noreply@example.com", alias="SENDGRID_FROM_EMAIL"
    )

    # AWS
    aws_access_key_id: str = Field(default="", alias="AWS_ACCESS_KEY_ID")
    aws_secret_access_key: str = Field(default="", alias="AWS_SECRET_ACCESS_KEY")
    aws_region: str = Field(default="us-east-1", alias="AWS_REGION")
    aws_s3_bucket: str = Field(default="", alias="AWS_S3_BUCKET")

    # =============================================================================
    # Security
    # =============================================================================
    password_min_length: int = Field(default=8, alias="PASSWORD_MIN_LENGTH")
    rate_limit_per_minute: int = Field(default=60, alias="RATE_LIMIT_PER_MINUTE")

    # =============================================================================
    # Feature Flags
    # =============================================================================
    feature_enable_registration: bool = Field(
        default=True, alias="FEATURE_ENABLE_REGISTRATION"
    )
    feature_enable_vendor_dashboard: bool = Field(
        default=True, alias="FEATURE_ENABLE_VENDOR_DASHBOARD"
    )
    feature_enable_recommendations: bool = Field(
        default=True, alias="FEATURE_ENABLE_RECOMMENDATIONS"
    )

    @field_validator("app_env")
    @classmethod
    def validate_environment(cls, v: str) -> str:
        """Validate and normalize environment name."""
        valid_envs = ["development", "staging", "production"]
        if v not in valid_envs:
            raise ValueError(f"Environment must be one of {valid_envs}")
        return v

    # Validators removed - using default values only

    class Config:
        env_file = ".env"
        case_sensitive = False
        extra = "ignore"


# Initialize settings
settings = Settings()


def parse_database_url(db_url: str) -> Tuple[str, str, str, str, str, str]:
    """
    Parse a database URL into components for Django settings.

    Args:
        db_url: Database URL in format: postgresql://user:password@host:port/database

    Returns:
        Tuple of (name, user, password, host, port, conn_max_age)
    """
    parsed = urlparse(db_url)

    return (
        parsed.path.lstrip('/') if parsed.path else 'postgres',  # database name
        parsed.username or 'postgres',  # user
        parsed.password or '',  # password
        parsed.hostname or 'localhost',  # host
        str(parsed.port or 5432),  # port
    )


# =============================================================================
# Django Settings Module
# =============================================================================

# Build paths
BASE_DIR = settings.base_dir

# Security WARNING: keep the secret key used in production secret!
SECRET_KEY = settings.secret_key

# SECURITY WARNING: don't run with debug turned on in production!
DEBUG = settings.debug
ALLOWED_HOSTS = settings.allowed_hosts

# Application definition
INSTALLED_APPS = [
    "django.contrib.admin",
    "django.contrib.auth",
    "django.contrib.contenttypes",
    "django.contrib.sessions",
    "django.contrib.messages",
    "django.contrib.staticfiles",
    # Third party
    "rest_framework",
    "rest_framework_simplejwt",
    "corsheaders",
    "django_filters",
    "drf_spectacular",
    # Local apps
    "backend.api",
    "backend.core",
]

MIDDLEWARE = [
    "django.middleware.security.SecurityMiddleware",
    "corsheaders.middleware.CorsMiddleware",
    "django.contrib.sessions.middleware.SessionMiddleware",
    "django.middleware.common.CommonMiddleware",
    "django.middleware.csrf.CsrfViewMiddleware",
    "django.contrib.auth.middleware.AuthenticationMiddleware",
    "django.contrib.messages.middleware.MessageMiddleware",
    "django.middleware.clickjacking.XFrameOptionsMiddleware",
    "django.middleware.gzip.GZipMiddleware",  # Response compression
]

ROOT_URLCONF = "backend.urls"

TEMPLATES = [
    {
        "BACKEND": "django.template.backends.django.DjangoTemplates",
        "DIRS": [],
        "APP_DIRS": True,
        "OPTIONS": {
            "context_processors": [
                "django.template.context_processors.debug",
                "django.template.context_processors.request",
                "django.contrib.auth.context_processors.auth",
                "django.contrib.messages.context_processors.messages",
            ],
        },
    },
]

WSGI_APPLICATION = "backend.wsgi.application"

# Database
db_name, db_user, db_password, db_host, db_port = parse_database_url(settings.database_url)

DATABASES = {
    "default": {
        "ENGINE": "django.db.backends.postgresql",
        "NAME": db_name,
        "USER": db_user,
        "PASSWORD": db_password,
        "HOST": db_host,
        "PORT": db_port,
        "CONN_MAX_AGE": 600,
        "OPTIONS": {
            "connect_timeout": 10,
        },
    }
}

# Cache (Redis)
CACHES = {
    "default": {
        "BACKEND": "django_redis.cache.RedisCache",
        "LOCATION": settings.redis_url,
        "OPTIONS": {
            "CLIENT_CLASS": "django_redis.client.DefaultClient",
        },
        "KEY_PREFIX": "ecommerce",
        "TIMEOUT": settings.cache_ttl,
    }
}

# Session (using Redis cache)
SESSION_ENGINE = "django.contrib.sessions.backends.cache"
SESSION_CACHE_ALIAS = "default"

# Password validation
AUTH_PASSWORD_VALIDATORS = [
    {
        "NAME": "django.contrib.auth.password_validation.UserAttributeSimilarityValidator",
    },
    {
        "NAME": "django.contrib.auth.password_validation.MinimumLengthValidator",
        "OPTIONS": {
            "min_length": settings.password_min_length,
        },
    },
    {
        "NAME": "django.contrib.auth.password_validation.CommonPasswordValidator",
    },
    {
        "NAME": "django.contrib.auth.password_validation.NumericPasswordValidator",
    },
]

# Internationalization
LANGUAGE_CODE = "en-us"
TIME_ZONE = settings.timezone
USE_I18N = True
USE_TZ = settings.use_tz

# Static files (CSS, JavaScript, Images)
STATIC_URL = "/static/"
STATIC_ROOT = os.path.join(BASE_DIR, "staticfiles")
MEDIA_URL = "/media/"
MEDIA_ROOT = os.path.join(BASE_DIR, "media")

# Default primary key field type
DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"

# Custom user model
AUTH_USER_MODEL = "core.User"

# REST Framework configuration
REST_FRAMEWORK = {
    "DEFAULT_AUTHENTICATION_CLASSES": [
        "rest_framework_simplejwt.authentication.JWTAuthentication",
    ],
    "DEFAULT_PERMISSION_CLASSES": [
        "rest_framework.permissions.IsAuthenticatedOrReadOnly",
    ],
    "DEFAULT_RENDERER_CLASSES": [
        "rest_framework.renderers.JSONRenderer",
    ],
    "DEFAULT_PARSER_CLASSES": [
        "rest_framework.parsers.JSONParser",
    ],
    "DEFAULT_FILTER_BACKENDS": [
        "django_filters.rest_framework.DjangoFilterBackend",
        "rest_framework.filters.OrderingFilter",
        "rest_framework.filters.SearchFilter",
    ],
    "DEFAULT_PAGINATION_CLASS": "rest_framework.pagination.PageNumberPagination",
    "PAGE_SIZE": 20,
    "EXCEPTION_HANDLER": "backend.api.exceptions.custom_exception_handler",
    "DEFAULT_SCHEMA_CLASS": "drf_spectacular.openapi.AutoSchema",
}

# JWT Settings
SIMPLE_JWT = {
    "ACCESS_TOKEN_LIFETIME": timedelta(minutes=int(settings.jwt_access_token_expire_minutes)),
    "REFRESH_TOKEN_LIFETIME": timedelta(days=int(settings.jwt_refresh_token_expire_days)),
    "ROTATE_REFRESH_TOKENS": True,
    "BLACKLIST_AFTER_ROTATION": True,
    "ALGORITHM": settings.jwt_algorithm,
    "SIGNING_KEY": settings.secret_key,
    "AUTH_HEADER_TYPES": ("Bearer",),
    "AUTH_TOKEN_CLASSES": ("rest_framework_simplejwt.tokens.AccessToken",),
}

# CORS Settings
CORS_ALLOWED_ORIGINS = settings.cors_origins
CORS_ALLOW_CREDENTIALS = True
CORS_ALLOW_HEADERS = [
    "accept",
    "accept-encoding",
    "authorization",
    "content-type",
    "dnt",
    "origin",
    "user-agent",
    "x-csrftoken",
    "x-request-id",
]
CORS_ALLOW_METHODS = [
    "DELETE",
    "GET",
    "OPTIONS",
    "PATCH",
    "POST",
    "PUT",
]

# Spectacular (OpenAPI) Settings
SPECTACULAR_SETTINGS = {
    "TITLE": "E-Commerce API",
    "DESCRIPTION": "API for E-Commerce Platform",
    "VERSION": "1.0.0",
    "SERVE_INCLUDE_SCHEMA": False,
    "SCHEMA_PATH_PREFIX": "/api/v1",
    "COMPONENT_SPLIT_REQUEST": True,
}

# Logging Configuration
LOGGING = {
    "version": 1,
    "disable_existing_loggers": False,
    "formatters": {
        "verbose": {
            "format": "{levelname} {asctime} {module} {process:d} {thread:d} {message}",
            "style": "{",
        },
        "simple": {
            "format": "{levelname} {asctime} {message}",
            "style": "{",
        },
    },
    "handlers": {
        "console": {
            "class": "logging.StreamHandler",
            "formatter": "verbose",
        },
    },
    "root": {
        "handlers": ["console"],
        "level": "INFO",
    },
    "loggers": {
        "django": {
            "handlers": ["console"],
            "level": "INFO",
            "propagate": False,
        },
        "backend": {
            "handlers": ["console"],
            "level": "INFO" if settings.app_env != "development" else "DEBUG",
            "propagate": False,
        },
    },
}

# Celery Configuration
CELERY_BROKER_URL = settings.celery_broker_url
CELERY_RESULT_BACKEND = settings.celery_result_backend
CELERY_TASK_SERIALIZER = "json"
CELERY_RESULT_SERIALIZER = "json"
CELERY_ACCEPT_CONTENT = ["json"]
CELERY_TIMEZONE = settings.timezone
CELERY_TASK_TRACK_STARTED = True
CELERY_TASK_TIME_LIMIT = settings.celery_task_timeout

# Elasticsearch Configuration
ELASTICSEARCH_DSL = {
    "default": {"hosts": [settings.elasticsearch_url]},
}
ELASTICSEARCH_INDEX_PREFIX = settings.elasticsearch_index_prefix

# Stripe Configuration
STRIPE_PUBLIC_KEY = settings.stripe_public_key
STRIPE_SECRET_KEY = settings.stripe_secret_key
STRIPE_WEBHOOK_SECRET = settings.stripe_webhook_secret

# Email Configuration (using SendGrid)
EMAIL_BACKEND = "django.core.mail.backends.smtp.EmailBackend"
EMAIL_HOST = "smtp.sendgrid.net"
EMAIL_PORT = 587
EMAIL_USE_TLS = True
EMAIL_HOST_USER = "apikey"
EMAIL_HOST_PASSWORD = settings.sendgrid_api_key
DEFAULT_FROM_EMAIL = settings.sendgrid_from_email

# AWS S3 Configuration (for media storage)
if settings.app_env == "production":
    # Production uses S3
    DEFAULT_FILE_STORAGE = "storages.backends.s3boto3.S3Boto3Storage"
    AWS_ACCESS_KEY_ID = settings.aws_access_key_id
    AWS_SECRET_ACCESS_KEY = settings.aws_secret_access_key
    AWS_STORAGE_BUCKET_NAME = settings.aws_s3_bucket
    AWS_S3_REGION_NAME = settings.aws_region
    AWS_S3_CUSTOM_DOMAIN = f"{settings.aws_s3_bucket}.s3.amazonaws.com"

# =============================================================================
# Production Security Settings
# =============================================================================
if settings.app_env == "production":
    # HTTPS Settings
    SECURE_SSL_REDIRECT = True
    SECURE_PROXY_SSL_HEADER = ("HTTP_X_FORWARDED_PROTO", "https")

    # HSTS Settings
    SECURE_HSTS_SECONDS = 31536000  # 1 year
    SECURE_HSTS_INCLUDE_SUBDOMAINS = True
    SECURE_HSTS_PRELOAD = True

    # Cookie Security
    SESSION_COOKIE_SECURE = True
    CSRF_COOKIE_SECURE = True
    SESSION_COOKIE_HTTPONLY = True
    CSRF_COOKIE_HTTPONLY = True
    SESSION_COOKIE_SAMESITE = "Lax"
    CSRF_COOKIE_SAMESITE = "Lax"

    # Security Headers
    SECURE_CONTENT_TYPE_NOSNIFF = True
    SECURE_BROWSER_XSS_FILTER = True
    X_FRAME_OPTIONS = "DENY"

# CSRF Settings
CSRF_COOKIE_NAME = "csrftoken"
CSRF_HEADER_NAME = "HTTP_X_CSRFTOKEN"

# Content Security Policy (if using django-csp)
# CSP_DEFAULT_SRC = ("'self'",)
# CSP_IMG_SRC = ("'self'", "data:", "https:")
# CSP_SCRIPT_SRC = ("'self'",)

# File Upload Settings
FILE_UPLOAD_MAX_MEMORY_SIZE = 10 * 1024 * 1024  # 10MB
DATA_UPLOAD_MAX_MEMORY_SIZE = 10 * 1024 * 1024  # 10MB
DATA_UPLOAD_MAX_NUMBER_FIELDS = 1000
